package com.netflix.streaming.platform.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {


    String uploadImage(String path, MultipartFile file) throws IOException;

    String uploadVideoLocally(String tempVideos, MultipartFile videoFile) throws IOException;
}
