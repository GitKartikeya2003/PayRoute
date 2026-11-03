package com.example.PayRouteBackend.order;


import com.example.PayRouteBackend.merchant.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {


    private final OrderService orderService;
    private final MerchantService merchantService;

    public OrderController(OrderService orderService, MerchantService merchantService) {
        this.orderService = orderService;
        this.merchantService = merchantService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentOrder create(@RequestHeader("X-Api-Key") String key
            , @Valid @RequestBody OrderDtos.Create request) {


        return orderService.create(merchantService.authenticate(key).id, request);

    }


    @GetMapping
    public List<PaymentOrder> list(@RequestHeader("X-Api-Key") String key) {
        return orderService.getPayments(merchantService.authenticate(key).id);
    }

    @GetMapping("/{id}")
    public PaymentOrder list(@RequestHeader("X-Api-Key") String key, @PathVariable String id) {
        return orderService.get(merchantService.authenticate(key).id, id);
    }


}
