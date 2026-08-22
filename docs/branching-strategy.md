# 分支与发布规范

本项目采用精简的 Git Flow。只长期维护三个环境分支，业务分支按需创建、合并后删除。

## 长期分支

| 分支 | 用途 | 合并来源 | 部署目标 |
| --- | --- | --- | --- |
| `main` | 已验证、可发布的生产代码 | `release/*`、`hotfix/*` | 生产环境 |
| `staging` | 发布前验收与回归测试 | `develop`、`release/*` | 预发布环境 |
| `develop` | 日常开发集成 | `feature/*`、`fix/*` | 开发环境 |

禁止直接在长期分支上开发。所有变更通过 Pull Request 合并。

## 临时分支

- `feature/<issue>-<description>`：新功能，例如 `feature/123-crm-sync`。
- `fix/<issue>-<description>`：普通缺陷修复。
- `release/<version>`：版本冻结、回归和发布准备，例如 `release/0.2.0`。
- `hotfix/<issue>-<description>`：从 `main` 创建的生产紧急修复。
- `chore/<description>`：构建、依赖、文档等非业务变更。

分支名只使用小写英文、数字和连字符。

## 标准流程

### 功能开发

1. 从 `develop` 创建 `feature/*` 或 `fix/*`。
2. 提交 Pull Request 到 `develop`，通过检查和评审后合并。
3. 将 `develop` 合并到 `staging`，完成预发布验收。
4. 从 `develop` 创建 `release/<version>`，只接受发布阻断修复。
5. 发布验收通过后，将 `release/*` 合并到 `main` 和 `develop`，并在 `main` 创建版本标签。

### 紧急修复

1. 从 `main` 创建 `hotfix/*`。
2. 验证后合并到 `main` 并发布。
3. 同步合并回 `develop` 和 `staging`，避免修复丢失。

## Pull Request 要求

- 至少一名非提交者评审。
- 后端测试、前端 lint、测试和构建全部通过。
- 禁止强制推送 `main`、`staging` 和 `develop`。
- 使用 squash merge 保持主线历史简洁。
- PR 应关联需求或缺陷，并说明变更范围、验证方式和回滚方案。

## 提交信息

采用 Conventional Commits：

```text
feat(customers): add customer profile page
fix(sync): retry failed CRM requests
docs(repo): document branching strategy
chore(deps): update frontend dependencies
```

允许的常用类型包括 `feat`、`fix`、`docs`、`refactor`、`test`、`chore` 和 `ci`。
