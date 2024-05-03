package by.bsuir.services;

import by.bsuir.dto.CommentRequestTo;
import by.bsuir.dto.CommentResponseTo;
import by.bsuir.entities.Comment;
import by.bsuir.entities.CommentKey;
import by.bsuir.exceptions.DeleteException;
import by.bsuir.exceptions.NotFoundException;
import by.bsuir.exceptions.UpdateException;
import by.bsuir.repository.CommentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.util.*;

@Service
@Validated
public class CommentService {
    @Autowired
    CommentRepository commentDao;

    public CommentResponseTo getCommentById(Long id) throws NotFoundException {
        Optional<Comment> comment = commentDao.findById(id).stream().findFirst();
        return comment.map(this::commentToCommentResponse).orElseThrow(() -> new NotFoundException("Comment not found!", 40004L));
    }

    public List<CommentResponseTo> getComments() {
        return toCommentResponseList(commentDao.findAll());
    }

    public CommentResponseTo saveComment(@Valid CommentRequestTo comment, String country) {
        Comment commentToSave = commentRequestToComment(comment);
        commentToSave.setId(getId());
        commentToSave.setCountry(getCountry(country));
        return commentToCommentResponse(commentDao.save(commentToSave));
    }

    public int deleteComment(Long id) throws DeleteException {
        Optional<Comment> comment = commentDao.findById(id).stream().findFirst();
        if (comment.isEmpty()) {
            return -1;
        } else {
            commentDao.deleteByCountryAndIssueIdAndId(comment.get().getCountry(), comment.get().getIssueId(), comment.get().getId());
            return 1;
        }
    }

    public CommentResponseTo updateComment(@Valid CommentRequestTo comment, String country) throws UpdateException {
        Comment commentToUpdate = commentRequestToComment(comment);
        commentToUpdate.setId(comment.getId());
        commentToUpdate.setCountry(getCountry(country));
        Optional<Comment> commentOptional = commentDao.findById(comment.getId()).stream().findFirst();
        if (commentOptional.isEmpty()) {
            throw new UpdateException("Comment not found!", 40004L);
        } else {
            commentDao.deleteByCountryAndIssueIdAndId(commentOptional.get().getCountry(), commentOptional.get().getIssueId(), commentOptional.get().getId());
            return commentToCommentResponse(commentDao.save(commentToUpdate));
        }
    }

    public List<CommentResponseTo> getCommentByIssueId(Long issueId) throws NotFoundException {
        List<Comment> comment = commentDao.findByIssueId(issueId);
        if (comment.isEmpty()) {
            throw new NotFoundException("Comment not found!", 40004L);
        }
        return toCommentResponseList(comment);
    }

    private CommentResponseTo commentToCommentResponse(Comment comment) {
        CommentResponseTo commentResponseTo = new CommentResponseTo();
        commentResponseTo.setIssueId(comment.getIssueId());
        commentResponseTo.setId(comment.getId());
        commentResponseTo.setContent(comment.getContent());
        return commentResponseTo;
    }

    private List<CommentResponseTo> toCommentResponseList(List<Comment> comments) {
        List<CommentResponseTo> response = new ArrayList<>();
        for (Comment comment : comments) {
            response.add(commentToCommentResponse(comment));
        }
        return response;
    }

    private Comment commentRequestToComment(CommentRequestTo requestTo) {
        Comment comment = new Comment();
        comment.setId(requestTo.getId());
        comment.setContent(requestTo.getContent());
        comment.setIssueId(requestTo.getIssueId());
        return comment;
    }

    private long getId (){
        int currentSecond = (int) (System.currentTimeMillis() / 1000);

        int shiftedTime = currentSecond << 10;

        int randomBits = new Random().nextInt(1 << 10);

        return Math.abs(shiftedTime | randomBits);
    }

    private String getCountry(String requestHeader){
        Map<String, Double> languageMap = getStringDoubleMap(requestHeader);
        Map<String, Double> loadMap = new HashMap<>();
        for (String country: languageMap.keySet()){
            loadMap.put(country, commentDao.countByCountry(country)*(1-languageMap.get(country)));
        }
        double minValue = Double.MAX_VALUE;
        String minKey = null;

        for (Map.Entry<String, Double> entry : loadMap.entrySet()) {
            if (entry.getValue() < minValue) {
                minValue = entry.getValue();
                minKey = entry.getKey();
            }
        }
        return minKey;
    }

    private static Map<String, Double> getStringDoubleMap(String requestHeader) {
        String[] languages = requestHeader.split(",");
        Map<String, Double> languageMap = new HashMap<>();
        for (String language : languages) {
            String[] parts = language.split(";");
            String lang = parts[0].trim();
            double priority = 1.0; // По умолчанию
            if (parts.length > 1) {
                String[] priorityParts = parts[1].split("=");
                priority = Double.parseDouble(priorityParts[1]);
            }
            languageMap.put(lang, priority);
        }
        return languageMap;
    }
}
