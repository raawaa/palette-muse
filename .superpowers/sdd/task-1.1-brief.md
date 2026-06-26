### Task 1.1: 创建 Android 项目

**Files:**
- Create: 整个 Android 项目结构（通过 `android create` 命令生成）

- [ ] **Step 1: 使用 Android CLI 创建项目**

```bash
cd /Users/yuwenjie/Code/palette-muse
android create empty-activity --name="Palette Muse" --output=./
```

Run: 见上述命令
Expected: 项目创建成功，生成 `app/build.gradle.kts`、`gradle/libs.versions.toml`、`MainActivity.kt` 等文件

- [ ] **Step 2: 验证生成的文件结构**

```bash
find . -type f -not -path '*/build/*' -not -path '*/gradle/wrapper/*' -not -path '*/.gradle/*' | sort
```

Expected: `app/src/main/java/com/example/palettemuse/MainActivity.kt` 等模板文件存在

- [ ] **Step 3: 清理临时文件并初始化 git**

```bash
rm -rf docs/ 2>/dev/null; mkdir -p docs/superpowers/specs docs/superpowers/plans
# 将之前的设计文档和计划文档移回
cp /dev/null/.claude/projects/-Users-yuwenjie-Code-palette-muse/tmp_spec.md docs/superpowers/specs/2026-06-26-palette-muse-design.md 2>/dev/null || true
git init
git add -A
git commit -m "chore: initial project scaffold from android CLI template"
```

- [ ] **Step 4: 验证项目可编译**

```bash
./gradlew assembleDebug --no-daemon 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

---

