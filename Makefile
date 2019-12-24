compose=docker-compose -f docker-compose.dev.yml -p api-worker-example

default: help

up: ## Spin up services
	$(compose) up -d

stop: ## Stop services
	$(compose) stop

down: ## Destroy all services and volumes
	$(compose) down -v

help: ## This help message
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' -e 's/:.*#/: #/' | column -t -s '##'
