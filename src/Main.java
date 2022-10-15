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
    private static final double alpha = 0.01;
    private static final double threshhold = 0.95;

    public static void main(String[] args) throws IOException {
        System.out.println("calculating!");
        String DEFAULT_PATH = "/home/obiwan/Documents/dist/";
        List<File> spam_anl_mails = getMails(Paths.get(DEFAULT_PATH, "spam-anlern"));
        List<File> ham_anl_mails = getMails(Paths.get(DEFAULT_PATH, "ham-anlern"));
        List<File> spam_kall_mails = getMails(Paths.get(DEFAULT_PATH, "spam-kallibrierung"));
        List<File> ham_kall_mails = getMails(Paths.get(DEFAULT_PATH, "ham-kallibrierung"));
        List<File> spam_test_mails = getMails(Paths.get(DEFAULT_PATH, "spam-test"));
        List<File> ham_test_mails = getMails(Paths.get(DEFAULT_PATH, "ham-test"));

        ham_w_occurrences = wordOccurrenceCounter(ham_anl_mails);
        spam_w_occurrences = wordOccurrenceCounter(spam_anl_mails);
        ham_w_occurrences.forEach((s, aDouble) -> spam_w_occurrences.putIfAbsent(s, alpha));
        spam_w_occurrences.forEach((s, aDouble) -> ham_w_occurrences.putIfAbsent(s, alpha));

        Map<String, Double> classifiedMails = classifyMails(spam_test_mails);
        double correct = classifiedMails.values().stream().filter(aDouble -> aDouble >= threshhold).count();
        System.out.printf("Spam correct class: %.2f%%", (correct / classifiedMails.size()) * 100);
        System.out.println();
        classifiedMails = classifyMails(ham_test_mails);
        correct = classifiedMails.values().stream().filter(aDouble -> aDouble < threshhold).count();
        System.out.printf("Ham correct class: %.2f%%", (correct / classifiedMails.size()) * 100);
    }

    private static Map<String, Double> classifyMails(List<File> mails) throws FileNotFoundException {
        Map<String, Double> classifiedMails = new HashMap<>();
        for (File mail :
                mails) {
            List<String> mail_words = mailWords(mail);
            double mail_spaminess = spaminess(mail_words);
            classifiedMails.put(mail.getName(), mail_spaminess);
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
        double pWnS = 0.5;
        double pWnH = 0.5;
        double spam_words_sum = spam_w_occurrences.values().stream().mapToDouble(Double::doubleValue).sum();
        double ham_words_sum = ham_w_occurrences.values().stream().mapToDouble(Double::doubleValue).sum();
        int powS, powH;
        powS = 0;
        powH = 0;
        double pow_jump = 100;
        for (String word :
                words) {
            if (spam_w_occurrences.containsKey(word) && ham_w_occurrences.containsKey(word)) {
                pWnS = pWnS * (spam_w_occurrences.get(word) / spam_words_sum);
                pWnH = pWnH * (ham_w_occurrences.get(word) / ham_words_sum);

                if (pWnS < Math.pow(10, -pow_jump)) {
                    pWnS = pWnS * Math.pow(10, pow_jump);
                    powS++;
                }
                if (pWnH < Math.pow(10, -pow_jump)){
                    pWnH = pWnH * Math.pow(10, pow_jump);
                    powH++;
                }
            }
        }
        if (powH > powS) {
            powH = powH - powS;
            powS = 0;
        } else if (powH < powS) {
            powS = powS - powH;
            powH = 0;
        } else {
            powS = 0;
            powH = 0;
        }
        return pWnS * Math.pow(10, -powS*pow_jump) / (pWnS * Math.pow(10, -powS*pow_jump) + pWnH * Math.pow(10, -powH*pow_jump)); // pSnW
    }
}