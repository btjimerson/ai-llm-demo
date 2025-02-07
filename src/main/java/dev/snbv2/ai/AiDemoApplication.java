package dev.snbv2.ai;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@SpringBootApplication
public class AiDemoApplication implements CommandLineRunner {

	private static final Log log = LogFactory.getLog(AiDemoApplication.class);

	@Autowired
	VectorStore vectorStore;

	@Value("${llm.use-embeddings}")
	String useEmbeddings;

	public static void main(String[] args) {
		SpringApplication.run(AiDemoApplication.class, args);
	}


	@Override
    public void run(String... args) throws Exception {
		
		if (!Boolean.valueOf(useEmbeddings)) return;

		PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resourceResolver.getResources("classpath:/docs/*.pdf");

        TextSplitter textSplitter = new TokenTextSplitter();

        DocumentReader pdfReader;
		
		for (Resource resource : resources) {
			pdfReader = new PagePdfDocumentReader(resource);
			vectorStore.add(textSplitter.apply(pdfReader.get()));
		}

        log.info(String.format("Loaded vector database with embeddings from %d files.", resources.length));

    }

}
