# 多工作树协作

- 开工前用 `git status --short --branch` 和 `git worktree list` 确认目录、分支及未提交改动的归属。并行任务各用自己的工作树。
- 交接记录写明分支、HEAD、未提交文件和设备分配。交接后，原会话停止在同一工作树写入或构建。
- 模拟器验收前核对设备序列号、安装包名与当前前台应用。若设备已被另一会话占用，等明确交接或使用独立模拟器。
- 交付前复查 `git status`。提交、推送和合并以用户给出的范围为准。

早期分工和设备记录保留在 Git 历史中。[历史协调记录](https://github.com/suiren0219/gdust-v2/blob/66f6b3f207abeaad2175abfc7a0e7116af324600/COORDINATION.md)
