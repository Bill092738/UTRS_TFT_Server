// 全局变量
global.userID = "User1";          // 用户ID
global.currentPosition = "(0,0)";  // 当前位置
global.connected = false;          // 连接状态
global.serverMessages = ds_list_create(); // 服务器消息列表

// 创建事件
function initialize() {
    // 注意: GameMaker 不直接支持 TCP 连接
    // 需要使用 extension 如 "GMNet" 或 "GMLive" 来实现
    // 以下为连接参数
    global.SERVER_HOST = "127.0.0.1";
    global.SERVER_PORT = 23363;
    
    // GUI 相关
    display_set_gui_size(800, 600);
    keyboard_string = "";
}

// 步进事件
function step() {
    // 检测按下回车键
    if (keyboard_check_pressed(vk_enter) && string_length(keyboard_string) > 0) {
        process_command(keyboard_string);
        keyboard_string = "";
    }
}

// 绘制 GUI 事件
function draw_gui() {
    // 绘制输入框背景
    draw_set_color(c_black);
    draw_rectangle(10, 560, 790, 590, false);
    
    // 绘制输入的文本
    draw_set_color(c_white);
    draw_text(15, 565, "> " + keyboard_string);
    
    // 绘制服务器消息
    var msg_count = ds_list_size(global.serverMessages);
    for(var i = 0; i < min(msg_count, 20); i++) {
        draw_text(15, 530 - (i * 20), global.serverMessages[| msg_count - 1 - i]);
    }
    
    // 绘制当前位置
    draw_text(15, 10, "当前位置: " + global.currentPosition);
}

// 命令处理函数
function process_command(command) {
    if (string_pos("/Help", command) == 1) {
        // 显示帮助信息
        add_message("可用指令:");
        add_message("1. /Move (x,y) - 移动到目标位置");
        add_message("2. /Broadcast target \"message\" - 广播消息");
        add_message("3. /Help - 显示帮助信息");
        
        // 发送到服务器
        send_to_server("Help()");
    }
    else if (string_pos("/Move", command) == 1) {
        // 解析移动命令
        var pos_start = string_pos("(", command);
        var pos_end = string_pos(")", command);
        if (pos_start > 0 && pos_end > pos_start) {
            var coords = string_copy(command, pos_start, pos_end - pos_start + 1);
            if (validate_coordinates(coords)) {
                var moveCommand = "Move(" + global.userID + ", " + global.currentPosition + ", " + coords + ")";
                send_to_server(moveCommand);
                global.currentPosition = coords;
            } else {
                add_message("指令格式错误！正确格式: /Move (x,y)");
            }
        }
    }
    else if (string_pos("/Broadcast", command) == 1) {
        // 解析广播命令
        var parts = split_broadcast_command(command);
        if (parts != undefined) {
            var broadcastCommand = "Broadcast(" + global.userID + ", " + parts[0] + ", \"" + parts[1] + "\")";
            send_to_server(broadcastCommand);
        } else {
            add_message("指令格式错误！正确格式: /Broadcast target \"message\"");
        }
    }
}

// 辅助函数
function validate_coordinates(coords) {
    // 验证坐标格式 (x,y)
    var pattern = "\\(\\d+,\\d+\\)";
    return string_match(coords, pattern);
}

function split_broadcast_command(command) {
    // 分割广播命令
    var target_start = string_pos(" ", command) + 1;
    var msg_start = string_pos("\"", command);
    var msg_end = string_last_pos("\"", command);
    
    if (msg_start > 0 && msg_end > msg_start) {
        var target = string_copy(command, target_start, msg_start - target_start - 1);
        var message = string_copy(command, msg_start + 1, msg_end - msg_start - 1);
        return [target, message];
    }
    return undefined;
}

function add_message(msg) {
    ds_list_add(global.serverMessages, msg);
    if (ds_list_size(global.serverMessages) > 100) {
        ds_list_delete(global.serverMessages, 0);
    }
}

/* 
网络相关功能实现指南:
1. 需要使用 GameMaker 的网络扩展来实现 TCP 连接
2. 建议使用如下扩展之一:
   - GMNet (推荐)
   - GMLive
   - Async Network

3. 网络连接实现框架:
function send_to_server(message) {
    if (global.connected) {
        // 使用选定的网络扩展发送消息
        network_send_message(message);
    } else {
        add_message("未连接到服务器");
    }
}

4. 接收服务器消息:
// 在网络事件中
function network_message_received(message) {
    add_message("[服务器] " + message);
}
*/

// 清理事件
function cleanup() {
    ds_list_destroy(global.serverMessages);
    // 断开网络连接
}