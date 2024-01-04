/*
 * Copyright 2014-2024 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.impl.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Simple tree for finding all values associated with a prefix that matches the search
 * key. The prefix is a simple ascii string. If unsupported characters are used in the
 * prefix or search key, then the prefix match will only check up to the unsupported
 * character and the caller will need to perform further checks on the returned value.
 */
final class PrefixTree {

  private final Lock lock = new ReentrantLock();

  private volatile Node root;
  private final Set<Query.KeyQuery> otherQueries;

  /** Create a new instance. */
  PrefixTree() {
    this.otherQueries = newSet();
  }

  private Node addQuery(Node node, Query.KeyQuery query) {
    if (query instanceof Query.In) {
      Query.In q = (Query.In) query;
      node.inQueries.add(q);
    } else {
      node.otherQueries.add(query);
    }
    return node;
  }

  private boolean removeQuery(Node node, Query.KeyQuery query) {
    if (query instanceof Query.In) {
      Query.In q = (Query.In) query;
      return node.inQueries.remove(q);
    } else {
      return node.otherQueries.remove(query);
    }
  }

  /**
   * Put a query into the tree.
   *
   * @param query
   *     Query to add, the prefix will be extracted from the query clause.
   */
  void put(Query.KeyQuery query) {
    if (query instanceof Query.In) {
      Query.In q = (Query.In) query;
      for (String v : q.values()) {
        put(v, q);
      }
    } else if (query instanceof Query.Regex) {
      Query.Regex q = (Query.Regex) query;
      put(q.pattern().prefix(), q);
    } else {
      otherQueries.add(query);
    }
  }

  /**
   * Put a value into the tree.
   *
   * @param prefix
   *     ASCII string that represents a prefix for the search key.
   * @param value
   *     Value to associate with the prefix.
   */
  void put(String prefix, Query.KeyQuery value) {
    if (prefix == null) {
      otherQueries.add(value);
    } else {
      lock.lock();
      try {
        Node node = root;
        if (node == null) {
          root = new Node(prefix, EMPTY);
          addQuery(root, value);
        } else {
          root = putImpl(node, prefix, 0, value);
        }
      } finally {
        lock.unlock();
      }
    }
  }

  private Node putImpl(Node node, String key, int offset, Query.KeyQuery value) {
    final int prefixLength = node.prefix.length();
    final int keyLength = key.length() - offset;
    final int commonLength = commonPrefixLength(node.prefix, key, offset);
    if (commonLength == 0 && prefixLength > 0) {
      // No common prefix
      Node n = addQuery(new Node(key.substring(offset), EMPTY), value);
      return new Node("", new Node[] {n, node});
    } else if (keyLength == prefixLength && commonLength == prefixLength) {
      // Fully matches, add the value to this node
      addQuery(node, value);
      return node;
    } else if (keyLength > prefixLength && commonLength == prefixLength) {
      // key.startsWith(prefix), put the value into a child
      int childOffset = offset + commonLength;
      int pos = find(node.children, key, childOffset);
      if (pos >= 0) {
        Node n = putImpl(node.children[pos], key, childOffset, value);
        return node.replaceChild(n, pos);
      } else {
        Node n = addQuery(new Node(key.substring(childOffset), EMPTY), value);
        return node.addChild(n);
      }
    } else if (prefixLength > keyLength && commonLength == keyLength) {
      // prefix.startsWith(key), make new parent node and add this node as a child
      int childOffset = offset + commonLength;
      Node n = new Node(node.prefix.substring(commonLength), node.children, node.inQueries, node.otherQueries);
      return addQuery(new Node(key.substring(offset, childOffset), new Node[] {n}), value);
    } else {
      // Common prefix is a subset of both
      int childOffset = offset + commonLength;
      Node[] children = {
          new Node(node.prefix.substring(commonLength), node.children, node.inQueries, node.otherQueries),
          addQuery(new Node(key.substring(childOffset), EMPTY), value)
      };
      return new Node(node.prefix.substring(0, commonLength), children);
    }
  }

  /**
   * Remove a value from the tree with the associated prefix.
   *
   * @param query
   *     Query to remove, the prefix will be extracted from the query clause.
   * @return
   *     Returns true if a value was removed from the tree.
   */
  boolean remove(Query.KeyQuery query) {
    if (query instanceof Query.In) {
      boolean removed = false;
      Query.In q = (Query.In) query;
      for (String v : q.values()) {
        removed |= remove(v, q);
      }
      return removed;
    } else if (query instanceof Query.Regex) {
      Query.Regex q = (Query.Regex) query;
      return remove(q.pattern().prefix(), q);
    } else {
      return otherQueries.remove(query);
    }
  }

  /**
   * Remove a value from the tree with the associated prefix.
   *
   * @param prefix
   *     ASCII string that represents a prefix for the search key.
   * @param value
   *     Value to associate with the prefix.
   * @return
   *     Returns true if a value was removed from the tree.
   */
  boolean remove(String prefix, Query.KeyQuery value) {
    if (prefix == null) {
      return otherQueries.remove(value);
    } else {
      lock.lock();
      try {
        boolean removed = false;
        Node node = root;
        if (node != null) {
          removed = removeImpl(node, prefix, 0, value);
          if (removed) {
            node = node.compress();
            root = node.isEmpty() ? null : node;
          }
        }
        return removed;
      } finally {
        lock.unlock();
      }
    }
  }

  private boolean removeImpl(Node node, String key, int offset, Query.KeyQuery value) {
    final int prefixLength = node.prefix.length();
    final int keyLength = key.length() - offset;
    final int commonLength = commonPrefixLength(node.prefix, key, offset);
    if (keyLength == prefixLength && commonLength == prefixLength) {
      // Fully matches, remove the value from this node
      return removeQuery(node, value);
    } else if (keyLength > prefixLength && commonLength == prefixLength) {
      // Try to remove from children
      int childOffset = offset + commonLength;
      int pos = find(node.children, key, childOffset);
      return pos >= 0 && removeImpl(node.children[pos], key, childOffset, value);
    } else {
      return false;
    }
  }

  /**
   * Get a list of values associated with a prefix of the search key.
   *
   * @param key
   *     Key to compare against the prefixes.
   * @return
   *     Values associated with a matching prefix.
   */
  List<Query.KeyQuery> get(String key) {
    List<Query.KeyQuery> result = new ArrayList<>();
    forEach(key, result::add);
    return result;
  }

  /**
   * Invokes the consumer function for each value associated with a prefix of the search key.
   *
   * @param key
   *     Key to compare against the prefixes.
   * @param consumer
   *     Function to call for matching values.
   */
  void forEach(String key, Consumer<Query.KeyQuery> consumer) {
    // In queries cannot have an empty value, so cannot be in the root set
    otherQueries.forEach(consumer);
    Node node = root;
    if (node != null) {
      forEachImpl(node, key, 0, consumer);
    }
  }

  private void forEachImpl(Node node, String key, int offset, Consumer<Query.KeyQuery> consumer) {
    final int prefixLength = node.prefix.length();
    final int keyLength = key.length() - offset;
    final int commonLength = commonPrefixLength(node.prefix, key, offset);

    if (commonLength == prefixLength) {
      // Prefix matches, consume other queries
      node.otherQueries.forEach(consumer);

      if (commonLength < keyLength) {
        // There is more to the key, check if there are also matches for child nodes
        int childOffset = offset + commonLength;
        int pos = find(node.children, key, childOffset);
        if (pos >= 0) {
          forEachImpl(node.children[pos], key, childOffset, consumer);
        }
      } else {
        // It is an exact match, consume in queries
        node.inQueries.forEach(consumer);
      }
    }
  }

  /**
   * Returns true if the tree is empty.
   */
  boolean isEmpty() {
    return otherQueries.isEmpty() && (root == null || root.isEmpty());
  }

  /**
   * Returns the overall number of values in the tree. The size is computed on demand
   * by traversing the tree, so this call may be expensive.
   */
  int size() {
    Node r = root;
    return (r == null ? 0 : r.size()) + otherQueries.size();
  }

  /**
   * Determine the length of the common prefix for two strings.
   *
   * @param str1
   *     First string to compare.
   * @param str2
   *     Second string to compare.
   * @param offset
   *     Offset in the second string for where to start.
   * @return
   *     Length of the common prefix for the two strings.
   */
  static int commonPrefixLength(String str1, String str2, int offset) {
    int length = Math.min(str1.length(), str2.length() - offset);
    for (int i = 0; i < length; ++i) {
      if (str1.charAt(i) != str2.charAt(offset + i)) {
        return i;
      }
    }
    return length;
  }

  private static int find(Node[] nodes, String key, int offset) {
    int s = 0;
    int e = nodes.length - 1;
    while (s <= e) {
      int mid = (s + e) >>> 1;
      int cmp = Character.compare(nodes[mid].prefix.charAt(0), key.charAt(offset));
      if (cmp == 0)
        return mid;
      else if (cmp < 0)
        s = mid + 1;
      else
        e = mid - 1;
    }
    return -1;
  }

  private static <T> Set<T> newSet() {
    // The copy on write implementation is used because in the hot path traversing the set of values
    // is the most important aspect.
    return new CopyOnWriteArraySet<>();
  }

  private static Set<Query.KeyQuery> asSet(Query.KeyQuery value) {
    Set<Query.KeyQuery> set = newSet();
    set.add(value);
    return set;
  }

  private static final Node[] EMPTY = new Node[0];

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PrefixTree that = (PrefixTree) o;
    return Objects.equals(root, that.root)
        && otherQueries.equals(that.otherQueries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(root, otherQueries);
  }

  private static class Node {

    final String prefix;
    final Node[] children;
    final Set<Query.In> inQueries;
    final Set<Query.KeyQuery> otherQueries;

    Node(String prefix, Node[] children, Set<Query.In> inQueries, Set<Query.KeyQuery> otherQueries) {
      this.prefix = Preconditions.checkNotNull(prefix, "prefix");
      this.children = Preconditions.checkNotNull(children, "children");
      this.inQueries = Preconditions.checkNotNull(inQueries, "inQueries");
      this.otherQueries = Preconditions.checkNotNull(otherQueries, "otherQueries");
      Arrays.sort(children, Comparator.comparing(n -> n.prefix));
    }

    Node(String prefix, Node[] children) {
      this(prefix, children, newSet(), newSet());
    }

    Node replaceChild(Node n, int i) {
      Node[] cs = new Node[children.length];
      System.arraycopy(children, 0, cs, 0, i);
      cs[i] = n;
      System.arraycopy(children, i + 1, cs, i + 1, children.length - i - 1);
      return new Node(prefix, cs, inQueries, otherQueries);
    }

    Node addChild(Node n) {
      Node[] cs = new Node[children.length + 1];
      System.arraycopy(children, 0, cs, 0, children.length);
      cs[children.length] = n;
      return new Node(prefix, cs, inQueries, otherQueries);
    }

    Node compress() {
      // Create list of compressed children, avoid allocating the list unless
      // there is a change.
      List<Node> cs = null;
      for (int i = 0; i < children.length; ++i) {
        Node child = children[i];
        Node c = child.compress();
        if (c != child || c.isEmpty()) {
          if (cs == null) {
            cs = new ArrayList<>(children.length);
            for (int j = 0; j < i; ++j) {
              cs.add(children[j]);
            }
          }
          if (!c.isEmpty()) {
            cs.add(c);
          }
        } else if (cs != null) {
          cs.add(child);
        }
      }

      // Return compressed node. Merge nodes if intermediates have no values.
      if (cs == null) {
        return this;
      } else if (inQueries.isEmpty() && otherQueries.isEmpty() && cs.size() == 1) {
        Node c = cs.get(0);
        String p = prefix + c.prefix;
        return new Node(p, EMPTY, c.inQueries, c.otherQueries);
      } else {
        return new Node(prefix, cs.toArray(EMPTY), inQueries, otherQueries);
      }
    }

    boolean isEmpty() {
      return inQueries.isEmpty() && otherQueries.isEmpty() && areAllChildrenEmpty();
    }

    private boolean areAllChildrenEmpty() {
      for (Node child : children) {
        if (!child.isEmpty()) {
          return false;
        }
      }
      return true;
    }

    int size() {
      int sz = inQueries.size() + otherQueries.size();
      for (Node child : children) {
        sz += child.size();
      }
      return sz;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Node node = (Node) o;
      return prefix.equals(node.prefix)
          && Arrays.equals(children, node.children)
          && inQueries.equals(node.inQueries)
          && otherQueries.equals(node.otherQueries);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(prefix, inQueries, otherQueries);
      result = 31 * result + Arrays.hashCode(children);
      return result;
    }
  }
}
