// 全局变量
global.lastPosition = "(0,0)"; // 记录上一次位置用于比较

// 位置翻译函数 - 在obj_player的Step事件中调用
function translate_position() {
    // 获取当前坐标并转换为服务器格式
    var current_x = floor(obj_player.x);
    var current_y = floor(obj_player.y);
    var current_pos = "(" + string(current_x) + "," + string(current_y) + ")";
    
    // 如果位置发生变化，发送Move命令
    if (current_pos != global.lastPosition) {
        var move_command = "Move(User1, " + global.lastPosition + ", " + current_pos + ")";
        // 更新lastPosition
        global.lastPosition = current_pos;
        
        /* 
        网络发送指南:
        1. 使用选择的网络扩展发送move_command
        2. 示例:
        network_send(move_command);
        */
        
        show_debug_message("发送移动命令: " + move_command);
    }
}

// 广播消息翻译函数 - 可以绑定到GUI按钮或键盘事件
function translate_broadcast(target, message) {
    if (string_length(message) > 0) {
        var broadcast_command = "Broadcast(User1, " + target + ", \"" + message + "\")";
        
        /* 
        网络发送指南:
        1. 使用选择的网络扩展发送broadcast_command
        2. 示例:
        network_send(broadcast_command);
        */
        
        show_debug_message("发送广播命令: " + broadcast_command);
    }
}

// Help命令处理函数 - 可以绑定到F1键或帮助按钮
function send_help_command() {
    var help_command = "Help()";
    
    /* 
    网络发送指南:
    1. 使用选择的网络扩展发送help_command
    2. 示例:
    network_send(help_command);
    */
    
    show_debug_message("发送帮助命令: " + help_command);
    
    // 在游戏中显示帮助信息
    show_message(
        "可用命令:\n" +
        "1. 移动角色 - 自动发送Move命令\n" +
        "2. 按T键发送广播 - /Broadcast target \"message\"\n" +
        "3. 按F1查看帮助 - Help()"
    );
}

// 以下代码放在obj_player的Create事件中
/*
// 初始化位置
global.lastPosition = "(0,0)";

// 创建广播输入变量
broadcast_target = "";
broadcast_message = "";
is_typing = false;
*/

// 以下代码放在obj_player的Step事件中
/*
// 处理移动
translate_position();

// 检测是否按T键开始输入广播消息
if (keyboard_check_pressed(ord("T")) && !is_typing) {
    is_typing = true;
    broadcast_target = get_string("输入广播目标:", "all");
    broadcast_message = get_string("输入广播消息:", "");
    
    if (broadcast_target != "" && broadcast_message != "") {
        translate_broadcast(broadcast_target, broadcast_message);
    }
    is_typing = false;
}

// 检测是否按F1键查看帮助
if (keyboard_check_pressed(vk_f1)) {
    send_help_command();
}
*/

/* 
使用说明:
1. 创建obj_player对象
2. 在obj_player的Create事件中复制初始化代码
3. 在obj_player的Step事件中复制事件处理代码
4. 实现网络发送功能（参考网络发送指南）
5. 可选：添加GUI元素来触发广播和帮助命令
*/