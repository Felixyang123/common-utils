package com.wly.samples.controller;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.wly.samples.pojo.req.QrCodeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

@RestController
@RequestMapping("/api/qrcode")
@Validated
public class QrCodeController {

    /**
     * 生成二维码图片 - 直接返回图片流
     */
    @GetMapping("/generate")
    public void generateQrCode(
            @RequestParam(value = "content") String content,
            @RequestParam(value = "width", defaultValue = "300") int width,
            @RequestParam(value = "height", defaultValue = "300") int height,
            @RequestParam(value = "format", defaultValue = "png") String format,
            HttpServletResponse response) throws IOException {
        
        // 设置响应头
        response.setContentType(getMediaType(format));
        response.setHeader("Content-Disposition", "inline; filename=\"qrcode." + format + "\"");
        
        // 生成二维码
        QrCodeUtil.generate(content, width, height, getImageFormat(format), response.getOutputStream());
    }
    
    /**
     * 生成二维码图片 - 返回Base64
     */
    @PostMapping("/generate/base64")
    public ResponseEntity<?> generateQrCodeBase64(@Valid @RequestBody QrCodeRequest request) {
        try {
            QrConfig config = buildQrConfig(request);
            
            // 生成Base64编码的二维码图片
            String base64 = QrCodeUtil.generateAsBase64(request.getContent(), config, getImageFormat(request.getFormat()));
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Base64Response(base64, request.getFormat()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("生成二维码失败: " + e.getMessage()));
        }
    }
    
//    /**
//     * 生成二维码图片 - 返回字节数组
//     */
//    @PostMapping("/generate/bytes")
//    public ResponseEntity<byte[]> generateQrCodeBytes(@Valid @RequestBody QrCodeRequest request) {
//        try {
//            QrConfig config = buildQrConfig(request);
//
//            // 生成二维码字节数组
//            byte[] bytes = QrCodeUtil.generatePng(request.getContent(), config);
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(getMediaType(request.getFormat()));
//            headers.setContentDispositionFormData("attachment", "qrcode." + request.getFormat());
//
//            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(null);
//        }
//    }
//
    /**
     * 生成带Logo的二维码
     */
    @PostMapping("/generate/with-logo")
    public void generateQrCodeWithLogo(@Valid @RequestBody QrCodeRequest request,
                                       HttpServletResponse response) throws IOException {
        try {
            QrConfig config = buildQrConfig(request);
            
            // 如果有Logo URL，下载并设置Logo
            if (request.getLogoUrl() != null && !request.getLogoUrl().isEmpty()) {
                BufferedImage logoImage = ImageIO.read(new URL(request.getLogoUrl()));
                config.setImg(logoImage);
                config.setRatio(20); // Logo占二维码的20%
            }
            
            response.setContentType(getMediaType(request.getFormat()));
            response.setHeader("Content-Disposition", "inline; filename=\"qrcode_with_logo." + request.getFormat() + "\"");
            
            QrCodeUtil.generate(request.getContent(), config, getImageFormat(request.getFormat()), response.getOutputStream());
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("生成二维码失败: " + e.getMessage());
        }
    }
    
    /**
     * 解码二维码
     */
    @PostMapping("/decode")
    public ResponseEntity<?> decodeQrCode(@RequestBody HttpServletRequest request) {
        try {
            BufferedImage image = ImgUtil.read(request.getInputStream());
            String content = QrCodeUtil.decode(image);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new DecodeResponse(content));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("解码二维码失败: " + e.getMessage()));
        }
    }
    
    /**
     * 生成彩色二维码
     */
    @PostMapping("/generate/colorful")
    public void generateColorfulQrCode(@Valid @RequestBody QrCodeRequest request,
                                       HttpServletResponse response) throws IOException {
        QrConfig config = buildQrConfig(request);
        
        // 设置自定义颜色

        response.setContentType(getMediaType(request.getFormat()));
        response.setHeader("Content-Disposition", "inline; filename=\"colorful_qrcode." + request.getFormat() + "\"");
        
        QrCodeUtil.generate(request.getContent(), config, getImageFormat(request.getFormat()), response.getOutputStream());
    }
    
    // 辅助方法
    private QrConfig buildQrConfig(QrCodeRequest request) {
        QrConfig config = new QrConfig();
        config.setWidth(request.getWidth());
        config.setHeight(request.getHeight());
        config.setMargin(request.getMargin());
        return config;
    }
    
    private String getMediaType(String format) {
        switch (format.toLowerCase()) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG_VALUE;
            case "gif":
                return MediaType.IMAGE_GIF_VALUE;
            default:
                return MediaType.IMAGE_PNG_VALUE;
        }
    }
    
    private String getImageFormat(String format) {
        if (format.equals("jpg")) {
            return "jpeg";
        }
        return format;
    }
    
    // 响应DTO类
    public static class Base64Response {
        private String base64;
        private String format;
        
        public Base64Response(String base64, String format) {
            this.base64 = base64;
            this.format = format;
        }
        
        // getters
        public String getBase64() { return base64; }
        public String getFormat() { return format; }
    }
    
    public static class DecodeResponse {
        private String content;
        
        public DecodeResponse(String content) {
            this.content = content;
        }
        
        public String getContent() { return content; }
    }
    
    public static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
    }
}