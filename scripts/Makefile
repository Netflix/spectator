BUILD_DIR       := gh-pages-build
PAGES_DIR       := $(BUILD_DIR)/gh-pages
VERSION         ?= latest
VERSION_DIR     := $(PAGES_DIR)/en/$(VERSION)
DOCS_DIR        := $(BUILD_DIR)/docs

.DEFAULT_GOAL   := $(VERSION)

$(BUILD_DIR):
	echo "rule: $@"
	mkdir -p $(BUILD_DIR)

$(DOCS_DIR): $(BUILD_DIR)
	echo "rule: $@"
	cp -rf docs mkdocs.yml $(BUILD_DIR)

$(PAGES_DIR): $(BUILD_DIR)
	echo "rule: $@"
	cd $(BUILD_DIR); git clone git@github.com:Netflix/spectator.git gh-pages
	cd $(PAGES_DIR); git checkout gh-pages

$(VERSION_DIR): $(PAGES_DIR)
	echo "rule: $@"
	mkdir -p $@

$(VERSION): $(DOCS_DIR) $(VERSION_DIR)
	echo "rule: $@"
	cd $(BUILD_DIR); mkdocs build --strict
	cp -rf $(BUILD_DIR)/site/* $(VERSION_DIR)/
	if [ "$(VERSION)" != "latest" ]; then  \
	  cd $(PAGES_DIR)/en/;                 \
	  rm -rf latest;                       \
	  ln -s $(VERSION) latest;             \
	fi

rebuild/$(VERSION): $(PAGES_DIR)
	rm -rf $(VERSION_DIR)
	make -f scripts/Makefile -e VERSION=$(VERSION)

clean:
	echo "rule: $@"
	rm -rf $(BUILD_DIR)
