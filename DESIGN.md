# Palette Muse — 设计系统 (Aura Aesthetic)

> 从 Stitch MCP 项目提取的设计系统，对应设计系统 "Aura Aesthetic"。

---

## Brand & Style

**品牌个性:** Effortlessly Chic — 高定时装编辑风格 × 现代社交发现平台

**美学方向:** Minimalism + Glassmorphism
- 留白营造"呼吸感" (luxury breathing room)
- 半透明玻璃层创造深度和轻盈感
- UI 作为"画廊"退居次要，让用户内容成为主角

---

## 调色板

### 品牌色 (Override Token)

| Token | Hex | 用途 |
|-------|-----|------|
| **Primary (Rose Gold)** | `#B76E79` | CTA、激活态、品牌签名 |
| **Secondary (Soft Lavender)** | `#E6E6FA` | 高亮、标签背景、玻璃层底色 |
| **Background (Pearl White)** | `#FDFBF7` | 核心背景色 |
| **Neutral (Ink)** | `#1A1A1A` | 正文、结构图标 |

### Material 完整色板 (从 Stitch 提取)

```yaml
primary: '#8a4853'
on-primary: '#ffffff'
primary-container: '#a6606b'
on-primary-container: '#fffbff'
inverse-primary: '#ffb2bc'

secondary: '#5c5d6e'
on-secondary: '#ffffff'
secondary-container: '#e1e1f5'
on-secondary-container: '#626374'

tertiary: '#5c5c59'
on-tertiary: '#ffffff'
tertiary-container: '#757572'
on-tertiary-container: '#fefcf8'

error: '#ba1a1a'
on-error: '#ffffff'
error-container: '#ffdad6'
on-error-container: '#93000a'

# Surface 层级
surface: '#fcf9f8'
surface-dim: '#dcd9d9'
surface-bright: '#fcf9f8'
surface-container-lowest: '#ffffff'
surface-container-low: '#f6f3f2'
surface-container: '#f0eded'
surface-container-high: '#eae7e7'
surface-container-highest: '#e5e2e1'

on-surface: '#1c1b1b'
on-surface-variant: '#524345'
inverse-surface: '#313030'
inverse-on-surface: '#f3f0ef'

outline: '#857374'
outline-variant: '#d7c1c3'
surface-tint: '#8c4b55'

background: '#fcf9f8'
on-background: '#1c1b1b'
surface-variant: '#e5e2e1'

# Fixed (用于 MD3 动态取色)
primary-fixed: '#ffd9dd'
primary-fixed-dim: '#ffb2bc'
on-primary-fixed: '#3a0915'
on-primary-fixed-variant: '#70343e'
secondary-fixed: '#e1e1f5'
secondary-fixed-dim: '#c5c5d8'
on-secondary-fixed: '#191b29'
on-secondary-fixed-variant: '#444655'
tertiary-fixed: '#e4e2de'
tertiary-fixed-dim: '#c8c6c3'
on-tertiary-fixed: '#1b1c1a'
on-tertiary-fixed-variant: '#474744'
```

### 色彩哲学 (Pearl & Petal)

> "色彩应用应稀疏。UI 应"消失"，让色彩斑斓的穿搭/实物照片成为主要视觉刺激。"

- Rose Gold — 高级不张扬，哑光金属调
- Soft Lavender — 梦幻柔和的点缀
- Pearl White — 暖基调白色，像高端文具
- Ink — 深不可测的黑色，最高可读性

---

## 排版

### Editorial Contrast 排版体系

| Token | Font | Size | Weight | Line H | Letter Sp | 用途 |
|-------|------|------|--------|--------|-----------|------|
| **display-lg** | Playfair Display | 40px | Bold (700) | 48px | -0.02em | 大标题、品牌展示 |
| **headline-lg** | Playfair Display | 32px | SemiBold (600) | 40px | — | 页头标题 |
| **headline-lg-mobile** | Playfair Display | 28px | SemiBold (600) | 36px | — | 手机端页头 |
| **title-md** | Plus Jakarta Sans | 18px | SemiBold (600) | 24px | — | 卡片标题、导航 |
| **body-lg** | Plus Jakarta Sans | 16px | Regular (400) | 24px | — | 正文 |
| **body-sm** | Plus Jakarta Sans | 14px | Regular (400) | 20px | — | 辅助文字 |
| **label-caps** | Plus Jakarta Sans | 12px | Bold (700) | 16px | 0.1em | 标签、元数据 |

**规则:**
- Playfair Display — 仅限标题、引语（时尚杂志感）
- Plus Jakarta Sans — 所有功能性 UI、导航、长描述
- 禁止在按钮和小号功能文本中使用衬线字体

---

## 布局与间距

```
留白规则:
  container-margin: 20px   (容器边距)
  gutter:           12px   (列间距)
  stack-sm:          8px   (小间距)
  stack-md:         16px   (中间距)
  stack-lg:         32px   (大间距)
  stack-xl:         64px   (超大间距 — 编辑段落间隔)
```

- **网格:** 移动端 4 列 Fluid Grid
- **画廊:** 非对称交错网格 (Pinterest/小红书风格)

---

## Elevation & Depth

视觉层次通过 Glassmorphism + Ambient Shadows 实现：

### 层级系统

| 层级 | 效果 | 用途 |
|------|------|------|
| **Base** | Pearl White 纯色 | 页面背景 |
| **Floating** | backdrop-blur(20px) + 60% white + 0.5px 白色描边 | 导航栏、FAB |
| **Shadow** | 主色/中性色 5-8% 透明度 + blur(20-30px) + y-offset | 卡片 |

- 禁止使用黑色阴影
- 阴影非常"软"且"长"

---

## 形状 (Hyper-Softness)

| 元素 | 圆角 | 
|------|------|
| 容器、卡片、按钮 | 最小 24px-32px (Pill-shaped) |
| Interactive (Save/Share) | full rounded (pill) |
| 图片 | 裁剪到软圆角 |
| 输入框 | 极软填充圆角 或 Ghost 式下边框 |

Token: `ROUND_FULL`（在 Compose 中对应 `RoundedCornerShape(24.dp - 32.dp)`）

---

## 组件规范

### Buttons
- **Primary:** Pill 形状 + Rose Gold 渐变背景
- **Secondary:** Glassmorphic 样式 (blur + 描边)

### Cards
- 无边线，仅靠软圆角图片剪影 + 极浅阴影
- 元数据紧接图片下方，留白充分

### Chips/Tags
- 半透明 Soft Lavender pill
- 高 letter-spacing 标签文字

### Input Fields
- Ghost 样式：仅下边框 1px，或极软填充圆角

### Lists
- 使用 whitespace 或 0.5px 极淡分割线（禁用粗分割线）

### Icons
- 细描边 1.5pt
- 激活态：Rose Gold 填充 + 弹性动画

---

## 屏幕截图 (Stitch 设计稿)

| 屏幕 | 描述 |
|------|------|
| 首页灵感流 | 品牌名 ChromaMuse + Today's Inspiration + Discover Palettes 网格 + 底部导航 |
| 穿搭色彩分析 | 穿搭照片 + 自动色彩标签 + 三段色板 (Primary/Secondary/Accent) |
| 实物色彩捕捉 | 相机取景器 + 实时匹配度 + 目标色对比 + 已捕捉色样历史 |
| 海报合成导出 | Moodboard 海报预览 + 分享/保存操作 |
| ChromaMuse App Flow | 应用流程示意图 |
