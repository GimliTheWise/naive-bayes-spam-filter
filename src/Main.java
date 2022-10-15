import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static Map<String, Double> anl_occurrences_ham;
    private static Map<String, Double> anl_occurrences_spam;
    private static final double ALPHA = 0.1;
    private static final double THRESHOLD = 0.95;
    private static final String DATASET = "kallibrierung"; //kallibrierung or test
    private static final String DEFAULT_PATH = "./resources/";


    public static void main(String[] args) throws IOException {
        System.out.println("Trying to read mails...");

        // read mails from directory into list
        List<File> spam_anl_mails = getMails(Paths.get(DEFAULT_PATH, "spam-anlern"));
        List<File> ham_anl_mails = getMails(Paths.get(DEFAULT_PATH, "ham-anlern"));
        List<File> spam_test_mails = getMails(Paths.get(DEFAULT_PATH, "spam-" + DATASET));
        List<File> ham_test_mails = getMails(Paths.get(DEFAULT_PATH, "ham-" + DATASET));

        System.out.println("counting occurrences of words in ham and spam anlern-datasets...");

        // count how many times words are found in all anlern mails
        // do this for ham and spam
        anl_occurrences_ham = wordOccurrenceCounter(ham_anl_mails);
        anl_occurrences_spam = wordOccurrenceCounter(spam_anl_mails);

        System.out.println("making sure all words occur in ham and spam.");
        System.out.println("Absent words being added with alpha=" + ALPHA + " occurrence...");
        anl_occurrences_ham.forEach((s, aDouble) -> anl_occurrences_spam.putIfAbsent(s, ALPHA));
        anl_occurrences_spam.forEach((s, aDouble) -> anl_occurrences_ham.putIfAbsent(s, ALPHA));

        System.out.println("Threshold for spam classification set at: " + THRESHOLD * 100 + "%");
        System.out.println("calculating spam probabilities on " + DATASET + " dataset...");

        Map<String, Double> classifiedMails = classifyMails(spam_test_mails);
        double correct_class = classifiedMails.values().stream().filter(aDouble -> aDouble >= THRESHOLD).count();
        System.out.printf("Correct spam classification ratio: %.2f%%", (correct_class / classifiedMails.size()) * 100);
        System.out.println();

        classifiedMails = classifyMails(ham_test_mails);
        correct_class = classifiedMails.values().stream().filter(aDouble -> aDouble < THRESHOLD).count();
        System.out.printf("Correct ham classification ratio: %.2f%%", (correct_class / classifiedMails.size()) * 100);
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

    /**
     * Calculates the probability of a mail being spam given its containing words
      * @param words of a mail
     * @return a double between 0 and 1
     */
    static double spaminess(List<String> words) {
        double Pr_W_given_that_S = 0.5;
        double Pr_W_given_that_H = 0.5;
        double spam_words_sum = anl_occurrences_spam.values().stream().mapToDouble(Double::doubleValue).sum();
        double ham_words_sum = anl_occurrences_ham.values().stream().mapToDouble(Double::doubleValue).sum();
        int pow_S, pow_H;
        pow_S = 0;
        pow_H = 0;
        double pow_jump = 100;
        for (String word :
                words) {
            if (anl_occurrences_spam.containsKey(word) && anl_occurrences_ham.containsKey(word)) {
                Pr_W_given_that_S = Pr_W_given_that_S * (anl_occurrences_spam.get(word) / spam_words_sum);
                Pr_W_given_that_H = Pr_W_given_that_H * (anl_occurrences_ham.get(word) / ham_words_sum);

                if (Pr_W_given_that_S < Math.pow(10, -pow_jump)) {
                    Pr_W_given_that_S = Pr_W_given_that_S * Math.pow(10, pow_jump);
                    pow_S++;
                }
                if (Pr_W_given_that_H < Math.pow(10, -pow_jump)) {
                    Pr_W_given_that_H = Pr_W_given_that_H * Math.pow(10, pow_jump);
                    pow_H++;
                }
            }
        }
        if (pow_H > pow_S) {
            pow_H = pow_H - pow_S;
            pow_S = 0;
        } else if (pow_H < pow_S) {
            pow_S = pow_S - pow_H;
            pow_H = 0;
        } else {
            pow_S = 0;
            pow_H = 0;
        }
        Pr_W_given_that_S = Pr_W_given_that_S * Math.pow(10, -pow_S * pow_jump);
        Pr_W_given_that_H = Pr_W_given_that_H * Math.pow(10, -pow_H * pow_jump);
        return Pr_W_given_that_S / (Pr_W_given_that_S + Pr_W_given_that_H); // probability of S given that W
    }
}