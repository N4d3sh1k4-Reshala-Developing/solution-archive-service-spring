package com.n4d3sh1k4.solution_archive_service.controller;

import com.n4d3sh1k4.solution_archive_service.model.DatasetEntry;
import com.n4d3sh1k4.solution_archive_service.repository.DatasetEntryRepository;
import com.n4d3sh1k4.solution_archive_service.service.MinioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/dataset")
@RequiredArgsConstructor
public class DatasetController {
    private final DatasetEntryRepository datasetRepository;
    private final MinioService minioService;

    @GetMapping
    public void downloadFullDataset(HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"latex_dataset.zip\"");
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            List<DatasetEntry> entries = datasetRepository.findAll();
            StringBuilder csvBuilder = new StringBuilder("file_name,text\n");

            for (DatasetEntry entry : entries) {
                String fileName = entry.getId() + ".png";

                String escapedLatex = entry.getLatexContent().replace("\"", "\"\"");
                csvBuilder.append(fileName).append(",\"").append(escapedLatex).append("\"\n");

                ZipEntry imageZipEntry = new ZipEntry("images/" + fileName);
                zos.putNextEntry(imageZipEntry);

                try (InputStream is = minioService.download(entry.getImagePath())) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }

            ZipEntry csvZipEntry = new ZipEntry("metadata.csv");
            zos.putNextEntry(csvZipEntry);
            zos.write(csvBuilder.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            log.info("Download complete.");

        }
        catch (IOException e) {
            log.error("Failed to export dataset", e);
        }
    }
}
