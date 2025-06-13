package com.aliwudi.marketplace.backend.api.gateway.debug; // Adjust package as necessary

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer; // Import ByteBuffer
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.HttpStatusCode;
// Removed java.util.List as it's no longer directly used

/**
 * A global filter to log the raw response from downstream microservices
 * as it passes through the API Gateway. This is useful for debugging
 * and understanding the exact data being sent back.
 */
@Component
@Slf4j // Uses Lombok for logging
public class ResponseLoggingGlobalFilter implements GlobalFilter, Ordered {

    /**
     * Defines the order in which this filter will run.
     * A negative value ensures it runs early in the filter chain,
     * allowing it to intercept the response very close to its raw state
     * from the downstream service, before many other gateway filters act on it.
     * If you wanted to see the response *after* all gateway filters have processed it,
     * you would set a very high positive order (e.g., Ordered.LOWEST_PRECEDENCE).
     *
     * @return The order value for this filter.
     */
    @Override
    public int getOrder() {
        return -1; // Runs very early in the response processing pipeline
    }

    /**
     * Filters the incoming request and outgoing response to log the response body and headers.
     *
     * @param exchange The current server web exchange.
     * @param chain The filter chain to proceed with.
     * @return A Mono<Void> indicating completion of the filter.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get the original response object and its buffer factory
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        // Decorate the original response to intercept and log the response body
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                // Check if the body is a Flux of DataBuffers (common for reactive streams)
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    // Join all data buffers into a single Mono<DataBuffer>
                    // This collects the entire response body before processing.
                    return DataBufferUtils.join(fluxBody)
                            .flatMap(joinedBuffer -> {
                                // --- Start of changes for ByteBufferIterator ---

                                // Get the ByteBufferIterator from the joined DataBuffer
                                ByteBufferIterator byteBufferIterator = joinedBuffer.readableByteBuffers();

                                // Calculate total bytes from the iterator
                                int totalBytes = 0;
                                while (byteBufferIterator.hasNext()) {
                                    ByteBuffer buffer = byteBufferIterator.next();
                                    totalBytes += buffer.remaining();
                                    // IMPORTANT: Rewind the buffer's position if you intend to read it again
                                    // The iterator advances the buffer's position.
                                    // However, for DataBuffer, the underlying ByteBuffer's position is managed internally.
                                    // For safety, we will re-iterate to fill the content array.
                                }
                                // Reset the iterator to the beginning for actual reading
                                byteBufferIterator = joinedBuffer.readableByteBuffers();


                                byte[] content = new byte[totalBytes];
                                int offset = 0;
                                while (byteBufferIterator.hasNext()) {
                                    ByteBuffer buffer = byteBufferIterator.next();
                                    int bytesToRead = buffer.remaining();
                                    buffer.get(content, offset, bytesToRead);
                                    offset += bytesToRead;
                                }

                                // Release the joined DataBuffer as it's no longer needed after reading its content.
                                // This is crucial for memory management in reactive applications.
                                DataBufferUtils.release(joinedBuffer);

                                // --- End of changes for ByteBufferIterator ---

                                // Convert the byte content to a String for logging (assuming UTF-8 for text)
                                String responseBody = new String(content, StandardCharsets.UTF_8);
                                HttpStatusCode statusCode = originalResponse.getStatusCode();
                                HttpHeaders responseHeaders = originalResponse.getHeaders();

                                // Log the response details
                                log.debug("--- Response from downstream microservice ---");
                                log.debug("Request Path: {}", exchange.getRequest().getPath());
                                log.debug("Status Code: {}", statusCode);
                                log.debug("Response Headers: {}", responseHeaders);
                                log.debug("Response Body (Truncated for brevity if large): {}",
                                    responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                                log.debug("------------------------------------------");

                                // Wrap the content back into a DataBuffer and send it to the next filter
                                // in the chain or directly to the client.
                                return super.writeWith(Mono.just(bufferFactory.wrap(content)));
                            })
                            // Handle any errors that might occur during body processing
                            .onErrorResume(e -> {
                                log.error("Error while logging response body: {}", e.getMessage());
                                // Continue with the original body to avoid breaking the response flow
                                return super.writeWith(body);
                            });
                }
                // If the body is not a Flux (e.g., Mono.empty()), just pass it through
                return super.writeWith(body);
            }

            /**
             * Handles writeAndFlushWith for completeness in reactive streams.
             * This simply flattens the nested publisher and delegates to writeWith.
             */
            @Override
            public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };

        // Mutate the exchange to use our decorated response and continue the filter chain
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
}
