package com.example.project.support;

import com.example.project.common.PageResult;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.OrderResponse;
import com.example.project.service.OrderService;
import com.example.project.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 规则客服 Provider（MVP，轻量 RAG）
 * <p>
 * 意图识别 + 实时查询当前用户订单/积分数据拼装回答（上下文=用户真实业务数据）。
 * 预留：真实大模型 Provider（OpenAI 兼容）接入后，可将"订单工具查询结果"作为 RAG 上下文注入提示词。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockChatProvider implements ChatProvider {

    private final OrderService orderService;
    private final PointsService pointsService;

    @Override
    public String channel() {
        return "mock";
    }

    @Override
    public String chat(Long userId, String message) {
        if (containsAny(message, "订单", "状态", "单号")) {
            return replyOrders(userId);
        }
        if (containsAny(message, "支付", "付款")) {
            return replyOrderStatus(userId, "PAID", "已支付");
        }
        if (containsAny(message, "发货", "物流", "收货", "快递")) {
            return replyOrderStatus(userId, "SHIPPED", "已发货");
        }
        if (containsAny(message, "取消", "退款")) {
            return "取消/退款说明：\n- 未支付订单可在「我的订单」直接取消；\n- 秒杀订单已支付可申请退款（自动回补库存并退还积分）；\n- 普通订单已支付暂不支持在线退款（后续开放）。";
        }
        if (containsAny(message, "积分")) {
            return replyPoints(userId);
        }
        if (containsAny(message, "优惠券", "券")) {
            return "优惠券可在「优惠券中心」领取，下单结算时可选券抵扣（满减规则见券说明）。";
        }
        if (containsAny(message, "你好", "您好", "在吗", "hi", "hello")) {
            return "您好，我是 LinkForge 智能客服（演示模式）。您可以问我：\n- 我的订单/支付/发货情况\n- 取消与退款规则\n- 积分余额\n- 优惠券说明";
        }
        return "这个问题我还在学习中（演示模式仅支持订单/支付/发货/退款/积分/优惠券查询）。如需深入帮助，请提供订单号或联系人工。";
    }

    private String replyOrders(Long userId) {
        List<OrderResponse> orders = recentOrders(userId);
        if (orders.isEmpty()) {
            return "您暂时没有订单，可以去商城逛逛或抢购限时秒杀～";
        }
        StringBuilder sb = new StringBuilder("您最近的订单如下：\n");
        orders.forEach(o -> sb.append("· 订单 ").append(o.getOrderNo())
                .append("，金额 ").append(o.getFinalAmount())
                .append(" 元，状态：").append(o.getStatus()).append("\n"));
        return sb.toString();
    }

    private String replyOrderStatus(Long userId, String status, String label) {
        List<OrderResponse> orders = recentOrders(userId);
        long count = orders.stream().filter(o -> status.equals(o.getStatus())).count();
        if (count == 0) {
            return "您最近没有" + label + "的订单。";
        }
        StringBuilder sb = new StringBuilder("您" + label + "的订单：\n");
        orders.stream().filter(o -> status.equals(o.getStatus()))
                .forEach(o -> sb.append("· ").append(o.getOrderNo()).append("（")
                        .append(o.getFinalAmount()).append(" 元）\n"));
        return sb.toString();
    }

    private String replyPoints(Long userId) {
        int balance = pointsService.getBalance(userId);
        return "您当前积分余额为 " + balance + " 分（1 元订单 ≈ 1 积分）。";
    }

    private List<OrderResponse> recentOrders(Long userId) {
        OrderQueryRequest query = new OrderQueryRequest();
        query.setUserId(userId);
        query.setPage(1);
        query.setSize(5);
        PageResult<OrderResponse> result = orderService.queryOrders(query);
        return result.getRecords();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
