package com.aliwudi.marketplace.backend.user.service;

import com.aliwudi.marketplace.backend.common.exception.OtpValidationException;
import static com.aliwudi.marketplace.backend.common.constants.RedisConstants.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * Service for generating, storing, and validating One-Time Passwords (OTPs) using Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private static final int OTP_LENGTH = 6;
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a new OTP, stores it in Redis with a TTL, and returns the OTP.
     *
     * @param userId The ID of the user (e.g., internal DB ID or Authorization Server authId) to associate with the OTP.
     * @param validityDuration The duration for which the OTP is valid.
     * @return Mono<String> emitting the generated OTP code.
     */
    public Mono<String> generateAndStoreOtp(String userId, Duration validityDuration) {
        return Mono.fromCallable(() -> {
            String otpCode = generateRandomOtp();
            String redisKey = OTP_PREFIX + userId;
            log.debug("Generated OTP {} for user {}. Storing in Redis for {} minutes.", otpCode, userId, validityDuration.toMinutes());
            return reactiveRedisTemplate.opsForValue().set(redisKey, otpCode, validityDuration)
                    .thenReturn(otpCode);
        })
        .flatMap(m -> m) // Flatten Mono<Mono<String>>
        .subscribeOn(Schedulers.boundedElastic()); // Offload blocking Redis calls if any, though ReactiveRedisTemplate is non-blocking
    }

    /**
     * Validates a provided OTP against the one stored in Redis for a given user.
     * If valid, the OTP is deleted from Redis to prevent reuse.
     *
     * @param userId The ID of the user.
     * @param providedOtp The OTP submitted by the user.
     * @return Mono<Boolean> emitting true if OTP is valid, false otherwise.
     * @throws OtpValidationException if the OTP is invalid or expired.
     */
    public Mono<Boolean> validateOtp(String userId, String providedOtp) {
        String redisKey = OTP_PREFIX + userId;
        log.debug("Attempting to validate OTP for user {}.", userId);

        return reactiveRedisTemplate.opsForValue().get(redisKey)
                .flatMap(storedOtp -> {
                    if (storedOtp == null) {
                        log.warn("OTP for user {} not found in Redis (or expired/already used).", userId);
                        return Mono.error(new OtpValidationException("Invalid or expired verification code."));
                    }
                    if (!storedOtp.equals(providedOtp)) {
                        log.warn("Invalid OTP provided for user {}. Mismatch.", userId);
                        return Mono.error(new OtpValidationException("Invalid verification code."));
                    }

                    // OTP is valid, delete from Redis to prevent reuse
                    log.info("OTP for user {} is valid. Deleting from Redis.", userId);
                    return reactiveRedisTemplate.delete(redisKey)
                            .thenReturn(true);
                })
                .switchIfEmpty(Mono.error(new OtpValidationException("Invalid or expired verification code."))) // Handles case where get() returns empty
                .doOnError(e -> log.error("Error during OTP validation for user {}: {}", userId, e.getMessage(), e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Generates a random numeric OTP string.
     * @return The generated OTP.
     */
    private String generateRandomOtp() {
        int max = (int) Math.pow(10, OTP_LENGTH) - 1;
        int min = (int) Math.pow(10, OTP_LENGTH - 1);
        int otp = secureRandom.nextInt(max - min + 1) + min;
        return String.format("%0" + OTP_LENGTH + "d", otp); // Pad with leading zeros
    }
}