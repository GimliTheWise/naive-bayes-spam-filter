import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static Map<String, Double> ham_w_occurrences;
    private static Map<String, Double> spam_w_occurrences;
    private static final double alpha = 0.9;

    public static void main(String[] args) throws IOException {
        System.out.println("Hello world!");
        String DEFAULT_PATH = "/home/obiwan/Documents/dist/";

        List<File> spam_anl_mails = getMails(Paths.get(DEFAULT_PATH, "spam-anlern"));
        List<File> ham_anl_mails = getMails(Paths.get(DEFAULT_PATH, "ham-anlern"));
        List<File> spam_kall_mails = getMails(Paths.get(DEFAULT_PATH, "spam-kallibrierung"));
        List<File> ham_kall_mails = getMails(Paths.get(DEFAULT_PATH, "ham-kallibrierung"));
        List<File> spam_test_mails = getMails(Paths.get(DEFAULT_PATH, "spam-test"));
        List<File> ham_test_mails = getMails(Paths.get(DEFAULT_PATH, "ham-test"));

        ham_w_occurrences = wordOccurrenceCounter(ham_anl_mails);
        spam_w_occurrences = wordOccurrenceCounter(spam_anl_mails);

        //Union of keys from both maps
        Set<String> unionHamSpam = new HashSet<>(ham_w_occurrences.keySet());
        unionHamSpam.addAll(spam_w_occurrences.keySet());

        Set<String> spam_only_words = new HashSet<>(unionHamSpam);
        spam_only_words.removeAll(ham_w_occurrences.keySet());

        Set<String> ham_only_words = new HashSet<>(unionHamSpam);
        ham_only_words.removeAll(spam_w_occurrences.keySet());

        System.out.println("Total words in spam: " + spam_w_occurrences.values().stream().mapToInt(Double::intValue).sum());
        System.out.println("Different words in spam: " + spam_w_occurrences.size());
        System.out.println("Total words in ham: " + ham_w_occurrences.values().stream().mapToInt(Double::intValue).sum());
        System.out.println("Different words in ham: " + ham_w_occurrences.size());

        ham_only_words.forEach(s -> spam_w_occurrences.put(s, alpha));
        spam_only_words.forEach(s -> ham_w_occurrences.put(s, alpha));
        // ham_w_occurrences.forEach((s, aDouble) -> spam_w_occurrences.putIfAbsent(s, 0.3));
        // spam_w_occurrences.forEach((s, aDouble) -> ham_w_occurrences.putIfAbsent(s, 0.3));

        System.out.println();
        System.out.println("Equality spam mails: " + spam_w_occurrences.values().stream().mapToDouble(Double::doubleValue).sum());
        System.out.println("Different words in spam: " + spam_w_occurrences.size());
        System.out.println("Equality ham mails: " + ham_w_occurrences.values().stream().mapToDouble(Double::doubleValue).sum());
        System.out.println("Different words in ham: " + ham_w_occurrences.size());
        System.out.println();
        System.out.println("Spam only words: " + spam_only_words.size());
        System.out.println("Ham only words: " + ham_only_words.size());

         Map<String, Double> classifiedMails = classifyMails(ham_test_mails);
         classifiedMails.forEach((s, aDouble) -> System.out.printf("%.4f%%%n", aDouble*100));
         double spam_found = classifiedMails.values().stream().filter(aDouble -> aDouble >= 0.95).count();

        System.out.println("Spam: " + (spam_found / classifiedMails.size())*100 + "%");
    }

    private static Map<String, Double> classifyMails(List<File> mails) throws FileNotFoundException {
        Map<String, Double> classifiedMails = new HashMap<>();
        for (File mail:
                mails) {
            List<String> mail_words = mailWords(mail);
            double mail_spaminess = spaminess(mail_words);
            classifiedMails.put(mail.getName(), mail_spaminess);
            //classifiedMails.put(mail.getName(), Math.round(mail_spaminess * 10000)/10000.0);
        }
        return classifiedMails;
    }

    static List<File> getMails(Path path) throws IOException {
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .map(Path::toFile).toList();
    }

    static Map<String, Double> wordOccurrenceCounter(List<File> mails) throws FileNotFoundException {
        Map<String, Double> word_occurrences = new HashMap<>();
        Scanner mailReader;
        for (File mail :
                mails) {
            mailReader = new Scanner(mail);
            while (mailReader.hasNextLine()) {
                String data = mailReader.nextLine();
                String[] words = data.split(" ");
                for (String word :
                        words) {
                    word = word.toLowerCase().trim();
                    if (word_occurrences.containsKey(word)) word_occurrences.merge(word, 1.0, Double::sum);
                    else word_occurrences.put(word, 1.0);
                }
            }
            mailReader.close();
        }
        return word_occurrences;
    }

    static List<String> mailWords(File mail) throws FileNotFoundException {
        Scanner mailReader;
        List<String> words = new ArrayList<>();
        mailReader = new Scanner(mail);
        while (mailReader.hasNextLine()) {
            String data = mailReader.nextLine();
            words.addAll(Arrays.stream(data.split(" ")).toList());
        }
        mailReader.close();
        return words.stream().map(s -> s.toLowerCase().trim()).toList();
    }

    static double spaminess(List<String> words) {
        double pWnS = 1;
        double pWnH = 1;
        double spam_words_sum = spam_w_occurrences.values().stream().mapToInt(Double::intValue).sum();
        double ham_words_sum = ham_w_occurrences.values().stream().mapToInt(Double::intValue).sum();
        int powS, powH;
        powS = powH = 0;
        int i = 0;
        for (String word :
                words) {
            if (spam_w_occurrences.containsKey(word) && ham_w_occurrences.containsKey(word)) {
                pWnS = pWnS * (spam_w_occurrences.get(word) / spam_words_sum);
                pWnH = pWnH * (ham_w_occurrences.get(word) / ham_words_sum);

                if (pWnS < Math.pow(10, -100)) {
                    pWnS = pWnS * Math.pow(10, 100);
                    powS++;
                }
                if (pWnH < Math.pow(10, -100)){
                    pWnH = pWnH * Math.pow(10, 100);
                    powH++;
                }
                i++;
            }
        }
        if (powH > powS) {
            powH = powH - powS;
            powS = 0;
        }
        else if (powH < powS){
            powS = powS - powH;
            powH = 0;
        }
        else powS = powH = 0;
        double pSnW = pWnS * Math.pow(10,powS) / (pWnS * Math.pow(10,powS) + pWnH * Math.pow(10,powH));
        //System.out.println(pWnS * Math.pow(10,powS) + "..." + pWnH * Math.pow(10,powH) + "..." + i + "..." + pSnW);
        return  pSnW; // pSnW
    }
}