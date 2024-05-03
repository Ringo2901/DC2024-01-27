package by.bsuir.controllers;

import by.bsuir.dto.CommentRequestTo;
import by.bsuir.dto.CommentResponseTo;
import by.bsuir.exceptions.NotFoundException;
import by.bsuir.repository.IssueRepository;
import jakarta.validation.Valid;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1.0/comments")
@CacheConfig(cacheNames = "commentCache")
@Validated
public class CommentController {
    @Autowired
    private RestClient restClient;
    @Autowired
    private KafkaConsumer<String, CommentResponseTo> kafkaConsumer;
    @Autowired
    private KafkaSender kafkaSender;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private IssueRepository issueRepository;
    private String inTopic = "InTopic";
    private String outTopic = "OutTopic";
    private String uriBase = "http://localhost:24130/api/v1.0/comments";

    @GetMapping
    public ResponseEntity<List<?>> getComments() {
        //kafkaSender.sendCustomMessage();
        return ResponseEntity.status(200).body(restClient.get()
                .uri(uriBase)
                .retrieve()
                .body(List.class));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentResponseTo> getComment(@PathVariable Long id) throws NotFoundException {
        Cache cache = cacheManager.getCache("comments");
        if (cache != null) {
            CommentResponseTo cachedResponse = cache.get(id, CommentResponseTo.class);
            if (cachedResponse != null) {
                return ResponseEntity.status(200).body(cachedResponse);
            }
        }
        CommentRequestTo commentRequestTo = new CommentRequestTo();
        commentRequestTo.setMethod("GET");
        commentRequestTo.setId(id);
        kafkaSender.sendCustomMessage(commentRequestTo, inTopic);
        CommentResponseTo response = listenKafka();

        if (response != null) {
            if (cache != null) {
                cache.put(id, response);
            }
            return ResponseEntity.status(200).body(response);
        } else {
            throw new NotFoundException("Comment not found", 404L);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) throws NotFoundException {
        CommentRequestTo commentRequestTo = new CommentRequestTo();
        commentRequestTo.setMethod("DELETE");
        commentRequestTo.setId(id);
        kafkaSender.sendCustomMessage(commentRequestTo, inTopic);
        listenKafka();
        Cache cache = cacheManager.getCache("comments");
        if (cache != null) {
            cache.evict(id);
        }

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @PostMapping
    public ResponseEntity<CommentResponseTo> saveComment(@RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguageHeader, @Valid @RequestBody CommentRequestTo comment) throws NotFoundException {
        comment.setCountry(acceptLanguageHeader);
        comment.setMethod("POST");
        if (!issueRepository.existsById(comment.getIssueId())){
            throw new NotFoundException("Issue not found!", 40400L);
        }
        kafkaSender.sendCustomMessage(comment, inTopic);
        return ResponseEntity.status(201).body(listenKafka());
    }

    @PutMapping()
    public ResponseEntity<CommentResponseTo> updateComment(@RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguageHeader, @RequestBody CommentRequestTo comment) throws NotFoundException {
        Cache cache = cacheManager.getCache("comments");
        if (cache != null) {
            cache.evict(comment.getId());
        }
        comment.setCountry(acceptLanguageHeader);
        comment.setMethod("PUT");
        kafkaSender.sendCustomMessage(comment, inTopic);
        return ResponseEntity.status(200).body(listenKafka());
    }

    @GetMapping("/byIssue/{id}")
    public ResponseEntity<?> getEditorByIssueId(@RequestHeader HttpHeaders headers, @PathVariable Long id) {
        return restClient.get()
                .uri(uriBase + "/byIssue/" + id)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .body(ResponseEntity.class);
    }

    private CommentResponseTo listenKafka() throws NotFoundException {
        ConsumerRecords<String, CommentResponseTo> records = kafkaConsumer.poll(Duration.ofMillis(50000));
        for (ConsumerRecord<String, CommentResponseTo> record : records) {

            String key = record.key();
            CommentResponseTo value = record.value();
            if (value == null) {
                throw new NotFoundException("Not found", 40400L);
            }
            long offset = record.offset();
            int partition = record.partition();
            System.out.println("Received message: key = " + key + ", value = " + value +
                    ", offset = " + offset + ", partition = " + partition);

            return value;
        }
        return null;
    }
}
