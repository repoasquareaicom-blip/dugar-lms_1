package dugar_lms_api.modules.contracts.controller;

import dugar_lms_api.modules.contracts.dto.LosContractReceiveRequest;
import dugar_lms_api.modules.contracts.dto.LosContractReceiveResponse;
import dugar_lms_api.modules.contracts.service.LosContractReceiveService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts/los")
public class LosContractReceiveController {

    private final LosContractReceiveService losContractReceiveService;

    public LosContractReceiveController(LosContractReceiveService losContractReceiveService) {
        this.losContractReceiveService = losContractReceiveService;
    }

    @PostMapping("/receive")
    public ResponseEntity<LosContractReceiveResponse> receive(@RequestBody LosContractReceiveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(losContractReceiveService.receive(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
