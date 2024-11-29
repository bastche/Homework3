import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilterFactory;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Paths;
import java.util.Scanner;

public class TABLESearch {
    public static void main(String[] args) throws Exception {
        String indexDirectory = "index_path"; // Replace with your index path

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDirectory)));
        IndexSearcher searcher = new IndexSearcher(reader);

        Scanner scanner = new Scanner(System.in);      
               
        System.out.print("Enter your query: ");
        String queryStr = scanner.nextLine().toString();

        // Create specific analyzers for certain fields
        Analyzer captionAnalyzer = CustomAnalyzer.builder()
        		.withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
        		.addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
        		.addTokenFilter(ASCIIFoldingFilterFactory.class) // Handle accents by transforming them
        		.addTokenFilter(StopFilterFactory.class,"ignoreCase", "true") // Remove stopwords in English
        		.addTokenFilter(SnowballPorterFilterFactory.class, "language", "English") // Stemming
        		.build();	
        Analyzer tableAnalyzer = CustomAnalyzer.builder()
        		.addCharFilter(HTMLStripCharFilterFactory.class) // Remove HTML tags
                .withTokenizer(WhitespaceTokenizerFactory.class) // Tokenize based on spaces
                .addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
                .addTokenFilter(ASCIIFoldingFilterFactory.class) // Remove accents
                .build();
        Analyzer referencesAnalyzer = CustomAnalyzer.builder() // To be defined
	            .withTokenizer(StandardTokenizerFactory.class) // Appropriate tokenization
	            .addTokenFilter(LowerCaseFilterFactory.class) // Convert to lowercase
	            .addTokenFilter(ASCIIFoldingFilterFactory.class) // Remove accents
	            .addTokenFilter(WordDelimiterGraphFilterFactory.class, 
	                            "splitOnCaseChange", "1", 
	                            "generateWordParts", "1") // Handle compound terms
	            .addTokenFilter(StopFilterFactory.class, "ignoreCase", "true") // Stopword list
	            .build();
               
        // Create queries for each field
        Query captionQuery = new QueryParser("caption", captionAnalyzer).parse(queryStr);
        Query tableQuery = new QueryParser("table", tableAnalyzer).parse(queryStr);
        Query referencesQuery = new QueryParser("references", referencesAnalyzer).parse(queryStr);

        // Apply boosts to prioritize fields
        captionQuery = new BoostQuery(captionQuery, 1.5f); // High boost for "caption"
        tableQuery = new BoostQuery(tableQuery, 1.2f); // Moderate boost for "table"
        referencesQuery = new BoostQuery(referencesQuery, 0.8f); // Low boost for "references"

        // Create the boolean query
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(captionQuery, BooleanClause.Occur.SHOULD);
        builder.add(tableQuery, BooleanClause.Occur.SHOULD);
        builder.add(referencesQuery, BooleanClause.Occur.SHOULD);
        
        BooleanQuery finalQuery = builder.build();
        
        // Execute the query
        org.apache.lucene.search.TopDocs hits = searcher.search(finalQuery, 100);
        StoredFields storedFields = searcher.storedFields();
        
        if (hits.scoreDocs.length > 1) {
	        System.out.println("Results: ");
	        for (int i = 0; i < hits.scoreDocs.length; i++) {		
	            Document doc = storedFields.document(hits.scoreDocs[i].doc);
	            System.out.println("Score: " + hits.scoreDocs[i].score);
	            System.out.println("Caption: " + doc.get("caption"));
	            String res_table = doc.get("table");
	            String truncated_table = res_table.length() > 200 ? res_table.substring(0, 200) + "......." : res_table;
	            System.out.println("Table: " + truncated_table);
	            System.out.println("------------------------");
	        }
        } else {
        	System.out.println("No matches were found for a table with the following terms: " + queryStr);
        }
        reader.close(); 
        scanner.close();
    }
}
