package com.jeltechnologies.portal.web;

import com.jeltechnologies.portal.connection.ConnectionInfo;
import com.jeltechnologies.portal.connection.ConnectionInfoProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PortalController {

    private final ConnectionInfoProvider connectionInfoProvider;
    private final QrCodeService qrCodeService;

    public PortalController(ConnectionInfoProvider connectionInfoProvider, QrCodeService qrCodeService) {
        this.connectionInfoProvider = connectionInfoProvider;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping({"/", "/index.html"})
    public String index(Model model) {
        model.addAttribute("info", connectionInfoProvider.connectionInfo());
        return "index";
    }

    @GetMapping(value = {"/json", "/api"}, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ConnectionInfo json() {
        return connectionInfoProvider.connectionInfo();
    }

    @GetMapping("/qr")
    public ResponseEntity<byte[]> qr() throws Exception {
        byte[] png = qrCodeService.generatePng(connectionInfoProvider.connectionInfo().vlessLink());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(png);
    }
}
