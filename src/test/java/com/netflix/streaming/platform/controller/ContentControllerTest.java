package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.MediaType;
import com.netflix.streaming.platform.payload.ContentDTO;
import com.netflix.streaming.platform.service.ContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ContentControllerTest {

    @InjectMocks
    private ContentController contentController;

    @Mock
    private ContentService contentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetContentById_Success() {
        ContentDTO dto = new ContentDTO();
        dto.setId(10L);
        dto.setTitle("Interstellar");
        dto.setContentType(MediaType.MOVIE);

        when(contentService.getContentById(10L)).thenReturn(dto);

        ResponseEntity<ContentDTO> response = contentController.getContentById(10L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Interstellar", response.getBody().getTitle());
        verify(contentService, times(1)).getContentById(10L);
    }

    @Test
    void testGetContentById_NotFound_ThrowsException() {
        when(contentService.getContentById(999L))
                .thenThrow(new ResourceNotFoundException("Content", "id", 999L));

        assertThrows(ResourceNotFoundException.class, () -> contentController.getContentById(999L));
        verify(contentService, times(1)).getContentById(999L);
    }
}

