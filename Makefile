.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

.PHONY: hooks
hooks: ## Install the pre-commit gate (git core.hooksPath=hooks); run once after cloning
	chmod +x hooks/pre-commit
	git config core.hooksPath hooks
	@echo "pre-commit hook installed (core.hooksPath=hooks)"

.PHONY: deps
deps: ## Fetch every jar pinned in deps.json into libs/ (once, and after deps.json changes)
	python3 scripts/fetch-deps.py

.PHONY: build
build: ## The Gradle gate: formatting, Error Prone and NullAway, the tests, 100% coverage, the compatibility matrix, both jars
	./gradlew build

.PHONY: test-scripts
test-scripts: ## Every script's tests, held to 100% line and branch coverage (needs coverage 7.6.10)
	python3 -m coverage run --branch --include='scripts/*' --omit='scripts/tests/*' -m unittest discover -s scripts/tests
	python3 -m coverage report --fail-under=100 -m

.PHONY: format
format: node_modules/.bin/prettier ## Apply Spotless to Java and Prettier to YAML, JSON and Markdown
	./gradlew spotlessApply
	node_modules/.bin/prettier --write '**/*.{yml,yaml,json,md}'

.PHONY: lint
lint: node_modules/.bin/prettier ## The non-Gradle checks over the whole tree: Prettier, shellcheck, actionlint, gitleaks, the changelog format
	node_modules/.bin/prettier --check '**/*.{yml,yaml,json,md}'
	git ls-files -z '*.sh' hooks/pre-commit | xargs -0 shellcheck
	actionlint
	python3 scripts/changelog.py check
	gitleaks detect --no-banner --redact

node_modules/.bin/prettier: package.json package-lock.json
	npm ci
	@touch node_modules/.bin/prettier
