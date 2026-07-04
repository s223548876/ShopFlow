package com.shopflow.admin;

import com.shopflow.admin.dto.AdminOrderResponse;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductRepository;
import com.shopflow.common.api.PageResponse;
import com.shopflow.order.InvalidOrderTransitionException;
import com.shopflow.order.Order;
import com.shopflow.order.OrderNotFoundException;
import com.shopflow.order.OrderPageRequest;
import com.shopflow.order.OrderRepository;
import com.shopflow.order.OrderStatus;
import com.shopflow.order.dto.OrderSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public AdminOrderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public PageResponse<OrderSummaryResponse> list(
            OrderStatus status,
            int page,
            int size,
            String sort
    ) {
        var orders = orderRepository.search(status, OrderPageRequest.of(page, size, sort));
        return PageResponse.from(orders.map(OrderSummaryResponse::from));
    }

    public AdminOrderResponse get(long orderId) {
        return AdminOrderResponse.from(orderRepository.findWithUserAndItemsById(orderId)
                .orElseThrow(OrderNotFoundException::new));
    }

    @Transactional
    public AdminOrderResponse updateStatus(long orderId, OrderStatus requestedStatus) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() == requestedStatus) {
            return AdminOrderResponse.from(order);
        }

        if (requestedStatus == OrderStatus.CANCELLED) {
            if (!isCancellable(order.getStatus())) {
                throw new InvalidOrderTransitionException();
            }
            restoreStock(order);
        } else if (!isAllowedNormalTransition(order.getStatus(), requestedStatus)) {
            throw new InvalidOrderTransitionException();
        }

        order.transitionTo(requestedStatus);
        return AdminOrderResponse.from(orderRepository.saveAndFlush(order));
    }

    private void restoreStock(Order order) {
        Map<Long, Integer> quantities = new TreeMap<>();
        order.getItems().forEach(item -> quantities.merge(
                item.getProductId(),
                item.getQuantity(),
                Integer::sum
        ));
        List<Long> productIds = new ArrayList<>(quantities.keySet());
        List<Product> products = productRepository.findAllByIdInOrderByIdForUpdate(productIds);
        if (products.size() != productIds.size()) {
            throw new IllegalStateException("Order references missing product");
        }
        for (int index = 0; index < products.size(); index++) {
            Product product = products.get(index);
            Long productId = productIds.get(index);
            if (!productId.equals(product.getId())) {
                throw new IllegalStateException("Order references missing product");
            }
            product.increaseStock(quantities.get(productId));
        }
    }

    private boolean isCancellable(OrderStatus status) {
        return status == OrderStatus.PENDING_PAYMENT
                || status == OrderStatus.PAID
                || status == OrderStatus.PROCESSING;
    }

    private boolean isAllowedNormalTransition(OrderStatus current, OrderStatus requested) {
        return current == OrderStatus.PAID && requested == OrderStatus.PROCESSING
                || current == OrderStatus.PROCESSING && requested == OrderStatus.SHIPPED
                || current == OrderStatus.SHIPPED && requested == OrderStatus.COMPLETED;
    }
}
