package com.netflix.streaming.platform.watchparty.application;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.exceptions.ResourceNotFoundException;
import com.netflix.streaming.platform.model.Content;
import com.netflix.streaming.platform.model.MediaType;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.repositories.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class WatchPartyAccessPolicyTest {

    @InjectMocks
    private WatchPartyAccessPolicy accessPolicy;

    @Mock
    private ContentRepository contentRepository;

    private User activeUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        activeUser = new User("Alice", "alice@test.com", "pass");
        activeUser.setActive(true);
    }

    @Test
    void testValidateMovieEligibility_Success() {
        Content movie = new Content();
        movie.setId(1L);
        movie.setTitle("Inception");
        movie.setContentType(MediaType.MOVIE);
        movie.setActive(true);

        when(contentRepository.findById(1L)).thenReturn(Optional.of(movie));

        Content validated = accessPolicy.validateContentEligibility(1L, activeUser);

        assertNotNull(validated);
        assertEquals(1L, validated.getId());
    }

    @Test
    void testValidateContentEligibility_RejectsSeriesInMVP() {
        Content series = new Content();
        series.setId(2L);
        series.setTitle("Stranger Things");
        series.setContentType(MediaType.TV_SHOW);
        series.setActive(true);

        when(contentRepository.findById(2L)).thenReturn(Optional.of(series));

        APIException ex = assertThrows(APIException.class,
                () -> accessPolicy.validateContentEligibility(2L, activeUser));
        assertTrue(ex.getMessage().contains("Watch Party MVP currently supports movies only"));
    }

    @Test
    void testValidateContentEligibility_RejectsInactiveContent() {
        Content inactiveMovie = new Content();
        inactiveMovie.setId(3L);
        inactiveMovie.setActive(false);
        inactiveMovie.setContentType(MediaType.MOVIE);

        when(contentRepository.findById(3L)).thenReturn(Optional.of(inactiveMovie));

        APIException ex = assertThrows(APIException.class,
                () -> accessPolicy.validateContentEligibility(3L, activeUser));
        assertTrue(ex.getMessage().contains("inactive"));
    }

    @Test
    void testValidateContentEligibility_ThrowsNotFoundWhenMissing() {
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> accessPolicy.validateContentEligibility(999L, activeUser));
    }
}

