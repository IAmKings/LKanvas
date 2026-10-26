# LKanvas sample R8 规则（PRD A-46：release 构建 minify 验证）
#
# 库自身语义：公开 API（io.lkanvas.**）由 consumer 混淆规则决定保留；
# 本 sample 是使用方，仅保留入口 Activity（AGP 默认已保留 manifest 组件）。

# kotlinx-serialization JSON DOM 反射不涉及（纯 DOM API），无需额外规则。
