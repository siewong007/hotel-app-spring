package com.hotelapp.core.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void databaseMapsToGeneric500() throws Exception {
        mockMvc.perform(get("/test/database"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"error\":\"Something went wrong on our end. Please try again.\"}"));
    }

    @Test
    void internalMapsToGeneric500() throws Exception {
        mockMvc.perform(get("/test/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"error\":\"Something went wrong on our end. Please try again.\"}"));
    }

    @Test
    void unauthorizedPolishesMessage() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Please sign in.\"}"));
    }

    @Test
    void unauthorizedEmptyMessageUsesFallback() throws Exception {
        mockMvc.perform(get("/test/unauthorized-empty"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"You need to sign in to continue.\"}"));
    }

    @Test
    void forbiddenMapsTo403WithFallback() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"You don't have permission to do that.\"}"));
    }

    @Test
    void badRequestMapsTo400Polished() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid dates.\"}"));
    }

    @Test
    void notFoundMapsTo404Polished() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Room 42 not found.\"}"));
    }

    @Test
    void conflictMapsTo409Polished() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"Dates overlap an existing booking.\"}"));
    }

    @Test
    void serviceUnavailableMapsTo503Polished() throws Exception {
        mockMvc.perform(get("/test/service-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"error\":\"Payment gateway down.\"}"));
    }

    @Test
    void tooManyRequestsMapsTo429Polished() throws Exception {
        mockMvc.perform(get("/test/too-many-requests"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json(
                        "{\"error\":\"Too many requests. Please slow down and try again.\"}"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void tooManyRequestsRetryAfterSetsHeaderAndBody() throws Exception {
        mockMvc.perform(get("/test/too-many-requests-retry-after"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().json("{\"error\":\"Hold on.\"}"));
    }

    @Test
    void profileIncompleteMapsTo422Envelope() throws Exception {
        mockMvc.perform(get("/test/profile-incomplete"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error")
                        .value("Complete your profile before making a booking."))
                .andExpect(jsonPath("$.code").value("profile_incomplete"))
                .andExpect(jsonPath("$.missing_profile_fields[0]").value("phone"))
                .andExpect(jsonPath("$.missing_profile_fields[1]").value("address"));
    }

    @Test
    void constraintViolationMapsTo400PolishedFirstMessage() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Must not be blank.\"}"));
    }

    @Test
    void methodArgumentNotValidMapsTo400PolishedFirstMessage() throws Exception {
        mockMvc.perform(post("/test/method-argument-not-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Check-out must be after check-in.\"}"));
    }

    @RestController
    static class ThrowingController {

        record BookingRequest(String checkIn, String checkOut) {}

        @GetMapping("/test/database")
        String database() {
            throw ApiError.database("db connection lost");
        }

        @GetMapping("/test/internal")
        String internal() {
            throw ApiError.internal("null pointer somewhere");
        }

        @GetMapping("/test/unauthorized")
        String unauthorized() {
            throw ApiError.unauthorized("please sign in");
        }

        @GetMapping("/test/unauthorized-empty")
        String unauthorizedEmpty() {
            throw ApiError.unauthorized("   ");
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw ApiError.forbidden("");
        }

        @GetMapping("/test/bad-request")
        String badRequest() {
            throw ApiError.badRequest("invalid dates");
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw ApiError.notFound("room 42 not found");
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw ApiError.conflict("dates overlap an existing booking");
        }

        @GetMapping("/test/service-unavailable")
        String serviceUnavailable() {
            throw ApiError.serviceUnavailable("payment gateway down");
        }

        @GetMapping("/test/too-many-requests")
        String tooManyRequests() {
            throw ApiError.tooManyRequests("");
        }

        @GetMapping("/test/too-many-requests-retry-after")
        String tooManyRequestsRetryAfter() {
            throw ApiError.tooManyRequestsRetryAfter("hold on", 30);
        }

        @GetMapping("/test/profile-incomplete")
        String profileIncomplete() {
            throw ApiError.profileIncomplete(List.of("phone", "address"));
        }

        @GetMapping("/test/constraint-violation")
        String constraintViolation() {
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            SampleInput input = new SampleInput();
            throw new ConstraintViolationException(validator.validate(input));
        }

        @PostMapping("/test/method-argument-not-valid")
        String methodArgumentNotValid(@RequestBody BookingRequest request)
                throws NoSuchMethodException, MethodArgumentNotValidException {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(request, "bookingRequest");
            bindingResult.addError(new FieldError(
                    "bookingRequest", "checkOut", "check-out must be after check-in"));
            throw new MethodArgumentNotValidException(new MethodParameter(
                    getClass().getDeclaredMethod("methodArgumentNotValid", BookingRequest.class), 0),
                    bindingResult);
        }

        static class SampleInput {
            @NotBlank
            String name;
        }
    }
}
