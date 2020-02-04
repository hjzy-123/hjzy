## protocol

protocol子工程用来存放各个子工程之间交互的通信协议


### 模块说明

- client2Manager 

  用于client（pc & web）与room manager的交互
  
  - http
    普通http post请求
    
  - websocket
    建立ws连接后交互消息