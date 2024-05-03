package by.bsuir.controllers;

import by.bsuir.dto.CommentRequestTo;
import by.bsuir.dto.CommentResponseTo;
import by.bsuir.services.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1.0/comments")
public class CommentController {
    @Autowired
    private CommentService commentService;
    @Autowired
    private KafkaSender kafkaSender;
    private String topic = "OutTopic";

    @KafkaListener(topics = "InTopic", groupId = "inGroup",
            containerFactory = "commentRequestToConcurrentKafkaListenerContainerFactory")
    void listenerWithMessageConverter(@Payload CommentRequestTo commentRequestTo) {
        if (Objects.equals(commentRequestTo.getMethod(), "GET")) {
            if (commentRequestTo.getId() != null) {
                kafkaSender.sendCustomMessage(getComment(commentRequestTo.getId()), topic);
            } else {
                // kafkaSender.sendCustomMessage(getComments());
            }
        } else {
            if (Objects.equals(commentRequestTo.getMethod(), "DELETE")) {
                kafkaSender.sendCustomMessage(deleteComment(commentRequestTo.getId()), topic);
            } else {
                if (Objects.equals(commentRequestTo.getMethod(), "POST")) {
                    kafkaSender.sendCustomMessage(saveComment(commentRequestTo.getCountry(), commentRequestTo), topic);
                } else {
                    if (Objects.equals(commentRequestTo.getMethod(), "PUT")) {
                        kafkaSender.sendCustomMessage(updateComment(commentRequestTo.getCountry(), commentRequestTo), topic);
                    }
                }
            }
        }
    }

    @GetMapping
    public List<CommentResponseTo> getComments() {
        return commentService.getComments();
    }

    @GetMapping("/{id}")
    public CommentResponseTo getComment(@PathVariable Long id) {
        return commentService.getCommentById(id);
    }

    @DeleteMapping("/{id}")
    public CommentResponseTo deleteComment(@PathVariable Long id) {
        if (commentService.deleteComment(id) > 0) {
            return new CommentResponseTo();
        } else {
            return null;
        }
    }

    @PostMapping
    public CommentResponseTo saveComment(@RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguageHeader, @RequestBody CommentRequestTo comment) {
        return commentService.saveComment(comment, acceptLanguageHeader);
    }

    @PutMapping()
    public CommentResponseTo updateComment(@RequestHeader(value = "Accept-Language", defaultValue = "en") String acceptLanguageHeader, @RequestBody CommentRequestTo comment) {
        return commentService.updateComment(comment, acceptLanguageHeader);
    }

    @GetMapping("/byIssue/{id}")
    public List<CommentResponseTo> getEditorByIssueId(@PathVariable Long id) {
        return commentService.getCommentByIssueId(id);
    }
}
