# RabbitMQ预约看房功能工作流程图

```mermaid
graph TD
    %% 样式定义
    style UI fill:#e1f5fe,stroke:#01579b,stroke-width:2px
    style WebAdmin fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    style WebApp fill:#e8f5e9,stroke:#1b5e20,stroke-width:2px
    style Service fill:#fff3e0,stroke:#e65100,stroke-width:2px
    style RabbitMQ fill:#fce4ec,stroke:#880e4f,stroke-width:2px
    style Database fill:#e0f2f1,stroke:#004d40,stroke-width:2px
    style User fill:#e8eaf6,stroke:#283593,stroke-width:2px
    style SMSService fill:#efebe9,stroke:#3e2723,stroke-width:2px
    style Notification fill:#efebe9,stroke:#3e2723,stroke-width:2px

    %% 用户操作
    User["👤 用户"] -->|1. 提交预约| WebApp["WebApp<br/>8081端口"]
    
    %% WebApp处理流程
    WebApp -->|2. 接收预约请求| Service["Service层<br/>ViewAppointmentService"]
    Service -->|3. 保存数据库| Database["MySQL<br/>view_appointment表"]
    
    %% 发送创建消息
    Service -->|4. 发送预约创建消息| RabbitMQ["📦 RabbitMQ<br/>Broker"]
    RabbitMQ -->|5. 路由到创建队列| Queue1["appointment.create.queue"]
    
    %% 消费者处理
    Queue1 -->|6. 处理预约创建| Consumer1["AppointmentMessageConsumer"]
    Consumer1 -->|7. 发送确认短信| SMSService["短信服务<br/>阿里云SMS"]
    SMSService -->|8. 短信通知| User
    
    %% 发送通知消息
    Consumer1 -->|9. 发送通知消息| RabbitMQ
    RabbitMQ -->|10. 路由到通知队列| Queue2["appointment.notify.queue"]
    Queue2 -->|11. 处理通知| Consumer2["AppointmentNotificationConsumer"]
    
    %% 后台管理流程
    User -->|12. 管理员操作| WebAdmin["WebAdmin<br/>8080端口"]
    WebAdmin -->|13. 更新预约状态| Service
    Service -->|14. 发送状态变更消息| RabbitMQ
    RabbitMQ -->|15. 广播通知| Consumer2
    
    %% 延迟消息处理
    subgraph 延迟处理
        Service -->|16. 设置延迟| RabbitMQ
        RabbitMQ -->|17. 24小时后到期| DLX["死信交换机<br/>DLX"]
        DLX -->|18. 转发到死信队列| DLXQueue["appointment.dlx.queue"]
        DLXQueue -->|19. 自动取消| Consumer1
    end
    
    %% 数据流向标注
    subgraph 数据流向说明
        direction LR
        A[请求流向: 用户→Web→Service→DB]
        B[消息流向: Service→RabbitMQ→Consumer]
        C[通知流向: Consumer→短信/通知服务]
        D[定时流向: 延迟消息→死信处理]
    end

    %% 消息类型说明
    subgraph 消息类型
        direction TB
        M1["• view.appointment.create<br/>预约创建消息"]
        M2["• view.appointment.notify<br/>通知消息"]
        M3["• view.appointment.expire<br/>过期消息"]
        M4["• appointment.dlx<br/>死信消息"]
    end
```

## 详细流程说明

### 1. 用户预约流程
```
用户 → 前台WebApp(8081) → Service层 → 数据库存储 → RabbitMQ消息 → 短信通知
```

### 2. 消息流转过程
```
Service层 → RabbitTemplate → Exchange → Queue → Consumer → 处理业务逻辑
```

### 3. 定时任务处理
```
Service层 → 延迟消息(24h) → 死信队列 → 自动取消未确认预约
```

### 4. 状态变更通知
```
管理员操作 → 后台WebAdmin(8080) → Service层 → 状态变更消息 → 通知相关方
```

## 关键组件说明

| 组件 | 说明 |
|------|------|
| **Exchange** | `appointment.exchange` - 预约相关交换机 |
| **Queue1** | `appointment.create.queue` - 预约创建队列 |
| **Queue2** | `appointment.notify.queue` - 通知消息队列 |
| **DLX** | `dlx.exchange` - 死信交换机 |
| **DLXQueue** | `appointment.dlx.queue` - 死信队列 |
| **Consumer1** | 预约创建消息消费者 |
| **Consumer2** | 通知消息消费者 |

## 消息队列优势

1. **异步处理**：短信发送不影响主业务流程
2. **削峰填谷**：高并发时缓冲请求
3. **可靠投递**：消息确认机制确保不丢失
4. **灵活扩展**：可轻松添加新的消息类型
5. **解耦**：服务间通过消息通信，降低耦合度