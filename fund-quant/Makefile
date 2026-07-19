.PHONY: install dev start test lint

PYTHON := python
VENV := .venv
VENV_PYTHON := $(VENV)/bin/python
UVICORN := $(VENV)/bin/uvicorn
PYTEST := $(VENV)/bin/pytest
RUFF := $(VENV)/bin/ruff

# 首次安装统一创建项目虚拟环境，避免依赖写入 pyenv 的全局 Python。
install:
	$(PYTHON) -m venv $(VENV)
	$(VENV_PYTHON) -m pip install --upgrade pip
	$(VENV_PYTHON) -m pip install -r requirements-dev.txt
	@test -f .env || cp .env.example .env

# 日常开发使用热重载；直接调用虚拟环境内命令，不要求手动 source。
dev:
	$(UVICORN) app.main:app --reload --port 8000

# 非热重载启动，适用于本地联调时希望进程更稳定的场景。
start:
	$(UVICORN) app.main:app --host 0.0.0.0 --port 8000

test:
	$(PYTEST)

lint:
	$(RUFF) check app tests

