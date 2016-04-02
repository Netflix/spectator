# Testing

Testing should be relatively straightforward if you are using injection for the registry. Consider a sample class:

```java
public class Foo {

  private final Counter counter;

  @Inject
  public Foo(Registry registry) {
    counter = registry.counter("foo");
  }

  public void doSomething() {
    counter.increment();
  }
}
```

## Simple Test

A basic standalone test class would look something like:

```java
public class FooTest {

  private Registry registry;
  private Foo foo;

  @Before
  public void init() {
    registry = new DefaultRegistry();
    foo = new Foo(registry);
  }

  @Test
  public void doSomething() {
    foo.doSomething();
    Assert.assertEquals(1, registry.counter("foo").count());
  }
}
```

## Guice Test

If using guice, then the `TestModule` can be used:

```java
public class FooTest {

  private Registry registry;
  private Foo foo;

  @Before
  public void init() {
    Injector injector = Guice.createInjector(new TestModule());
    registry = injector.getInstance(Registry.class);
    foo = injector.getInstance(Foo.class);
  }

  @Test
  public void doSomething() {
    foo.doSomething();
    Assert.assertEquals(1, registry.counter("foo").count());
  }
}
```
