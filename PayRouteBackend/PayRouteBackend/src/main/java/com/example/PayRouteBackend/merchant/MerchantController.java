package com.example.PayRouteBackend.merchant;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantService merchantService;


    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantDtos.Registered register(@Valid @RequestBody MerchantDtos.Register r) {

        return merchantService.register(r);
    }

    @GetMapping("/me")
    public Merchant me(@RequestHeader("X-Api-Key") String key) {

        return merchantService.authenticate(key);

    }

    @PutMapping("/me")
    public Merchant update(@RequestHeader("X-Api-Key") String key, @Valid @RequestBody MerchantDtos.Update r) {
        return merchantService.update(merchantService.authenticate(key).id, r);
    }


}
