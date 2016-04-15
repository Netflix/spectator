BUILD_DIR       := gh-pages-build
PAGES_DIR       := $(BUILD_DIR)/gh-pages
VERSION         ?= latest
VERSION_DIR     := $(PAGES_DIR)/en/$(VERSION)
REPORTS_DIR     := $(VERSION_DIR)/reports
JAVADOC_DIR     := $(VERSION_DIR)/javadoc
DOCS_DIR        := $(BUILD_DIR)/docs
REPORTS_PAGE    := $(DOCS_DIR)/reports.md

DOC_TARGETS     := $(addprefix $(JAVADOC_DIR)/,$(wildcard spectator-*))
REPORT_TARGETS  := $(addprefix $(REPORTS_DIR)/,$(wildcard spectator-*))

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

$(REPORTS_DIR): $(VERSION_DIR)
	echo "rule: $@"
	mkdir -p $@
	./gradlew jacocoTestReport build

$(REPORTS_DIR)/%: $(DOCS_DIR) $(REPORTS_DIR)
	echo "rule: $@"
	mkdir -p $@
	cp -rf $(notdir $@)/build/reports/* $@/
	echo ""                                                        >> $(REPORTS_PAGE)
	echo "## $(notdir $@)"                                         >> $(REPORTS_PAGE)
	echo ""                                                        >> $(REPORTS_PAGE)
	echo "* [JavaDoc](javadoc/$(notdir $@)/index.html)"            >> $(REPORTS_PAGE)
	echo "* [Tests](reports/$(notdir $@)/tests/index.html)"        >> $(REPORTS_PAGE)
	echo "* [Coverage](reports/$(notdir $@)/jacoco/index.html)"    >> $(REPORTS_PAGE)
	echo "* [PMD](reports/$(notdir $@)/pmd/main.html)"             >> $(REPORTS_PAGE)
	echo "* [FindBugs](reports/$(notdir $@)/findbugs/main.html)"   >> $(REPORTS_PAGE)

$(JAVADOC_DIR): $(VERSION_DIR)
	echo "rule: $@"
	mkdir -p $@
	./gradlew javadoc

$(JAVADOC_DIR)/%: $(JAVADOC_DIR)
	echo "rule: $@"
	mkdir -p $@
	cp -rf $(notdir $@)/build/docs/javadoc/* $@/

$(VERSION): $(DOC_TARGETS) $(REPORT_TARGETS)
	echo "rule: $@"
	cd $(BUILD_DIR); mkdocs build --strict
	cp -rf $(BUILD_DIR)/site/* $(VERSION_DIR)/

rebuild/$(VERSION): $(PAGES_DIR)
	rm -rf $(VERSION_DIR)
	make -f scripts/Makefile -e VERSION=$(VERSION)

clean:
	echo "rule: $@"
	rm -rf $(BUILD_DIR)
	./gradlew clean
