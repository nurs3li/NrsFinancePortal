package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.ReceiptUploadResponseDto;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FundReceiptStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "jpg", "jpeg", "png");
    private static final long MAX_SIZE_BYTES = 10L * 1024L * 1024L;
    private final Path root = Paths.get(System.getProperty("java.io.tmpdir"), "nrs-finance", "receipts");

    public ReceiptUploadResponseDto store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Receipt file is required");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Receipt file max size is 10MB");
        }
        String original = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "receipt.bin";
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("Only PDF/JPG/JPEG/PNG allowed");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = root.resolve(id + "_" + safeName);
        try {
            Files.createDirectories(root);
            Files.write(target, file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Receipt file cannot be saved", e);
        }
        return new ReceiptUploadResponseDto(
                id,
                "/api/fund-requests/receipts/" + id,
                original,
                file.getSize()
        );
    }

    public StoredReceipt load(String receiptId) {
        if (!StringUtils.hasText(receiptId)) {
            throw new IllegalArgumentException("Receipt id required");
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, receiptId + "_*")) {
            for (Path p : stream) {
                byte[] bytes = Files.readAllBytes(p);
                String fileName = p.getFileName().toString();
                int idx = fileName.indexOf('_');
                String original = idx >= 0 ? fileName.substring(idx + 1) : fileName;
                String ext = extensionOf(fileName);
                MediaType mediaType = switch (ext) {
                    case "pdf" -> MediaType.APPLICATION_PDF;
                    case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
                    case "png" -> MediaType.IMAGE_PNG;
                    default -> MediaType.APPLICATION_OCTET_STREAM;
                };
                Resource resource = new ByteArrayResource(bytes);
                return new StoredReceipt(original, mediaType, resource);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read receipt file", e);
        }
        throw new IllegalArgumentException("Receipt not found");
    }

    private String extensionOf(String fileName) {
        int i = fileName.lastIndexOf('.');
        if (i < 0 || i == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredReceipt(String originalFileName, MediaType mediaType, Resource resource) {
    }
}
