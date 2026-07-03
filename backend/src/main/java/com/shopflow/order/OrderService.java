package com.shopflow.order;

import com.shopflow.cart.Cart;
import com.shopflow.cart.CartItem;
import com.shopflow.cart.CartRepository;
import com.shopflow.cart.InsufficientStockException;
import com.shopflow.cart.ProductUnavailableException;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductRepository;
import com.shopflow.common.api.PageResponse;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OrderService(
            CartRepository cartRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository
    ) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .filter(found -> !found.getItems().isEmpty())
                .orElseThrow(CartEmptyException::new);
        List<CartLine> lines = cart.getItems().stream()
                .map(CartLine::from)
                .sorted(Comparator.comparing(CartLine::productId))
                .toList();
        List<Long> productIds = lines.stream().map(CartLine::productId).toList();
        List<Product> products = productRepository.findAllByIdInOrderByIdForUpdate(productIds);

        validateLockedProducts(lines, products);

        BigDecimal totalAmount = new BigDecimal("0.00");
        for (int index = 0; index < lines.size(); index++) {
            totalAmount = totalAmount.add(
                    products.get(index).getPrice().multiply(BigDecimal.valueOf(lines.get(index).quantity()))
            );
        }

        Order order = new Order(cart.getUser(), totalAmount);
        for (int index = 0; index < lines.size(); index++) {
            CartLine line = lines.get(index);
            Product product = products.get(index);
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(line.quantity()));
            product.decreaseStock(line.quantity());
            order.addItem(
                    product,
                    product.getName(),
                    product.getPrice(),
                    line.quantity(),
                    subtotal
            );
        }

        Order saved = orderRepository.saveAndFlush(order);
        cart.getItems().clear();
        return OrderResponse.from(saved);
    }

    public PageResponse<OrderSummaryResponse> list(long userId, int page, int size, String sort) {
        var orders = orderRepository.findByUserId(userId, OrderPageRequest.of(page, size, sort));
        return PageResponse.from(orders.map(OrderSummaryResponse::from));
    }

    public OrderResponse get(long userId, long orderId) {
        return OrderResponse.from(orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(OrderNotFoundException::new));
    }

    @Transactional
    public OrderResponse pay(long userId, long orderId) {
        Order order = orderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderTransitionException();
        }
        order.pay(Instant.now());
        return OrderResponse.from(orderRepository.saveAndFlush(order));
    }

    private void validateLockedProducts(List<CartLine> lines, List<Product> products) {
        if (lines.size() != products.size()) {
            throw new ProductUnavailableException();
        }
        for (int index = 0; index < lines.size(); index++) {
            CartLine line = lines.get(index);
            Product product = products.get(index);
            if (!line.productId().equals(product.getId()) || !product.isActive()) {
                throw new ProductUnavailableException();
            }
            if (line.quantity() > product.getStock()) {
                throw new InsufficientStockException();
            }
        }
    }

    private record CartLine(Long productId, int quantity) {

        static CartLine from(CartItem item) {
            return new CartLine(item.getProduct().getId(), item.getQuantity());
        }
    }
}
