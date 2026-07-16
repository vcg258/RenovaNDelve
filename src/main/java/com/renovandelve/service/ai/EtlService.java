package com.renovandelve.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
@Service
@RequiredArgsConstructor
public class EtlService {
    private final VectorStore vectorStore; // 벡터 스토어 RAG를 사용하기 위함
    private final JdbcTemplate jdbcTemplate; // 벡터 스토어의 전체 삭제를 위함

    // ETL추출을 할때 keyword 키워드를 지정하기 위해 @keyword(@keyword:) = keyword로 형식 지정 (싱글턴)
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("@keyword:?\\s*(.+?)\\s*$", Pattern.MULTILINE);

    /**
     * 업로드 파일 내용을 텍스트로 추출 -> 변환(임베딩) -> 적재 하는 메서드(벡터화)
     * @param file 업로드 파일
     * @return 추출 결과 내용
     * @throws IOException 호환되는 파일이 아닐경우 예외
     */
    public String etlFormFile(MultipartFile file) throws IOException {
        deleteAll();
        deleteChatMemory();

        // 업로드 파일 추출
        List<Document> documents = extractFormFile(file);
        if (documents == null) {
            throw new IOException(".txt, .json, .pdf, .doc, .docx 파일 중에 하나를 올려주세요.");
        }
        log.info("추출된 Documents : {}", documents.size());

        String contextType = file.getContentType();

        // .text == True, .doc, .docx == False
        boolean isContextType = contextType.equals("text/plain") || contextType.contains("wordprocessingml");
        List<Document> finalDocument;

        if (isContextType) {
            StringBuilder sb = new StringBuilder();

            for (Document doc : documents) {
                // 하나의 Document당 줄바꿈
                sb.append(doc.getText()).append("\n");
            }
            String rawText = sb.toString();

            // 구분자가 있으면 분리
            if (KEYWORD_PATTERN.matcher(rawText).find()) {
                finalDocument = parseByKeywordMarker(rawText, file.getOriginalFilename());
                log.info("@keyword 구분자 있음, 분리 : {}개", finalDocument.size());
            } else { // 아니면 없음으로 추출
                documents.forEach(doc -> doc.getMetadata().putAll(
                        Map.of("keyword", "키워드 없음", "source", file.getOriginalFilename())
                ));
                finalDocument = documents;
                log.info("@keyword 구분자 없음");
            }
        } else { // 동일
            documents.forEach(doc -> doc.getMetadata().putAll(
                    Map.of("keyword", "키워드 없음", "source", file.getOriginalFilename())
            ));
            finalDocument = documents;
        }

        // 청킹 (청크별로 나눔)
        List<Document> transformDocuments = transform(finalDocument);
        log.info("변환된 Documents : {}", transformDocuments.size());

        vectorStore.add(transformDocuments);

        return "올린 문서 추출-변환-적재 완료";
    }

    /* HELPER METHODS */
    /**
     * 업로드 파일의 타입에 따라 알맞은 Reader 호출후 리스트에 저장 하는 메서드
     * @param file 업로드 파일
     * @return 백터데이터
     * @throws IOException 호환 파일이 아닐 경우 예외
     */
    private List<Document> extractFormFile(MultipartFile file) throws IOException {
        String contextType = file.getContentType();
        if (contextType == null) {
            throw new IOException("잘못된 파일 타입");
        }

        Resource resource = new ByteArrayResource(file.getBytes());
        List<Document> documents = null;
        // TEXT
        if (contextType.equals("text/plain")) {
            DocumentReader reader = new TextReader(resource);
            documents = reader.read();
        // PDF
        } else if (contextType.equals("application/pdf")) {
            DocumentReader reader = new PagePdfDocumentReader(resource);
            documents = reader.read();
        // WORD
        } else if (contextType.contains("wordprocessingml")) {
            DocumentReader reader = new TikaDocumentReader(resource);
            documents = reader.read();
        // JSON
        } else if (contextType.equals("application/json")) {
            JsonReader reader = new JsonReader(resource, jsonMap -> Map.of(
                    "keyword", jsonMap.getOrDefault("keyword", "키워드 없음"),
                    "source", file.getOriginalFilename() // source는 파일명으로 지정
            ));
            documents = reader.read();
        }
        return documents;
    }

    /**
     * RAG에 들어갈 파일에서 마커되어있는 키워드를 추출함 (마커 표시막 @keyword)
     * @param rawText keyword
     * @param sourceFileName 업로드 파일명
     * @return 구분자(마커표시)를 기준으로 keyword를 나눈 메타데이터
     */
    private List<Document> parseByKeywordMarker (String rawText, String sourceFileName) {
        // 키워드 마커가 있는 블록을 배열에 담음
        String[] blocks = rawText.split("(?=@keyword)");
        List<Document> result = new ArrayList<>();

        for (String block : blocks) {
            // 배열 값의 공백 제거 후 계속
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            // 키워드 마커가 없는경우 넘어감
            Matcher matcher = KEYWORD_PATTERN.matcher(trimmed);
            if (!matcher.find()) continue;

            String keyword = matcher.group(1).strip();
            String content = trimmed.substring(matcher.end()).strip();

            // 내용이 비어있으면 넘어감
            if (content.isEmpty()) continue;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("keyword", keyword);
            metadata.put("source", sourceFileName);

            result.add(new Document(content, metadata));

        }

        return result;
    }

    /**
     * 추출한 Document를 분할하여 키워드를 추출하는 메서드
     * @param documents 업로드 파일의 내용을 추출한 바이트 리스트
     * @return 텍스트를 토큰 단위로 분할하여 키워드 추출한 메타데이터
     */
    private List<Document> transform(List<Document> documents) {
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(1000) // 청크당 최대 토큰 수
                .withMinChunkSizeChars(100) // 청크간 오버랩 토큰 수 (문맥 유지)
                .withMinChunkLengthToEmbed(5) // 최소 청크 토큰 수
                .withMaxNumChunks(10000) // 최대 청크 토큰 수
                .withKeepSeparator(true) // 문장 경계에서 분할 여부
                .build();

        // 토큰 기준으로 분할한 Document를 담은 리스트
        List<Document> transformDocuments = tokenTextSplitter.apply(documents);

        return transformDocuments;
    }

    /**
     * 백터 스토어 전체 삭제
     */
    private void deleteAll() {
        jdbcTemplate.update("TRUNCATE TABLE renova_n_delve.public.vector_store");
    }

    /**
     * ChatMemory 전체 삭제
     */
    private void deleteChatMemory() {
        jdbcTemplate.update("TRUNCATE TABLE renova_n_delve.public.spring_ai_chat_memory");
    }
}
