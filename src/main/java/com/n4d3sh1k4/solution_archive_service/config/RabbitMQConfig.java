package com.n4d3sh1k4.solution_archive_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String OCR_EXCHANGE = "ocr.events";
    public static final String OCR_RESULTS_QUEUE = "ocr.results.queue";
    public static final String OCR_COMPLETED_ROUTING_KEY = "ocr.completed";

    public static final String FEEDBACK_DELAY_EXCHANGE = "feedback.delay.exchange";
    public static final String FEEDBACK_DELAY_QUEUE = "feedback.delay.queue";
    
    public static final String FEEDBACK_PROCESS_EXCHANGE = "feedback.process.exchange";
    public static final String FEEDBACK_PROCESS_QUEUE = "feedback.process.queue";

    public static final String CAS_EXCHANGE = "cas.events";
    public static final String CAS_RESULTS_QUEUE = "cas.results.queue";
    public static final String CAS_COMPLETED_ROUTING_KEY = "cas.completed";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // --- OCR Integration ---
    @Bean
    public DirectExchange ocrExchange() {
        return new DirectExchange(OCR_EXCHANGE);
    }

    @Bean
    public Queue ocrResultsQueue() {
        return new Queue(OCR_RESULTS_QUEUE);
    }

    @Bean
    public Binding bindingOcrResults(Queue ocrResultsQueue, DirectExchange ocrExchange) {
        return BindingBuilder.bind(ocrResultsQueue).to(ocrExchange).with(OCR_COMPLETED_ROUTING_KEY);
    }

    // --- Feedback Delay Mechanism (DLQ) ---
    @Bean
    public DirectExchange feedbackProcessExchange() {
        return new DirectExchange(FEEDBACK_PROCESS_EXCHANGE);
    }

    @Bean
    public Queue feedbackProcessQueue() {
        return new Queue(FEEDBACK_PROCESS_QUEUE);
    }

    @Bean
    public Binding bindingFeedbackProcess(Queue feedbackProcessQueue, DirectExchange feedbackProcessExchange) {
        return BindingBuilder.bind(feedbackProcessQueue).to(feedbackProcessExchange).with("");
    }

    @Bean
    public DirectExchange feedbackDelayExchange() {
        return new DirectExchange(FEEDBACK_DELAY_EXCHANGE);
    }

    @Bean
    public Queue feedbackDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // Set DLX to route expired messages to process exchange
        args.put("x-dead-letter-exchange", FEEDBACK_PROCESS_EXCHANGE);
        args.put("x-dead-letter-routing-key", ""); 
        // 30 minutes = 30 * 60 * 1000 = 1800000 ms
        args.put("x-message-ttl", 1800000); 
        return new Queue(FEEDBACK_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding bindingFeedbackDelay(Queue feedbackDelayQueue, DirectExchange feedbackDelayExchange) {
        return BindingBuilder.bind(feedbackDelayQueue).to(feedbackDelayExchange).with("");
    }

    // --- CAS Integration ---
    @Bean
    public DirectExchange casExchange() {
        return new DirectExchange(CAS_EXCHANGE);
    }

    @Bean
    public Queue casResultsQueue() {
        return new Queue(CAS_RESULTS_QUEUE);
    }

    @Bean
    public Binding bindingCasResults(Queue casResultsQueue, DirectExchange casExchange) {
        return BindingBuilder.bind(casResultsQueue).to(casExchange).with(CAS_COMPLETED_ROUTING_KEY);
    }
}
