package uz.fb;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SiCheckinHandler extends CheckinHandler {
    private final CheckinProjectPanel panel;

    public SiCheckinHandler(CheckinProjectPanel panel) {
        this.panel = panel;
    }

    @Override
    public ReturnResult beforeCheckin() {

        Project project = panel.getProject();

        Map<String, List<String>> fileErrors = new HashMap<>();
        int totalErrorCount = 0; // Total errors count

        Iterable<VirtualFile> virtualFiles = panel.getVirtualFiles();

        String regexPattern = "SI\\(\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*,\\s*\"([^\"]*)\"\\s*\\)";
        Pattern pattern = Pattern.compile(regexPattern);

        for (VirtualFile virtualFile : virtualFiles) {
            if (virtualFile.getExtension() != null && virtualFile.getExtension().equalsIgnoreCase("jsp")) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile == null) continue;

                String text = psiFile.getText();
                Matcher matcher = pattern.matcher(text);

                String fileName = virtualFile.getName();

                while (matcher.find()) {
                    String fullMatch = matcher.group(0);
                    String rus = matcher.group(1);
                    String uzKrill = matcher.group(2);
                    String uzLot = matcher.group(3);
                    String eng = matcher.group(4);

                    // Get or create a list of errors in a file
                    List<String> errors = fileErrors.computeIfAbsent(fileName, k -> new ArrayList<>());

                    // Check 1: Is there an empty string?
                    if (rus.isEmpty() || uzKrill.isEmpty() || uzLot.isEmpty() || eng.isEmpty()) {
                        errors.add("Empty translation: " + fullMatch);
                        totalErrorCount++;
                    }

                    // Check 2: Are there any duplicates?
                    Set<String> translations = new HashSet<>();
                    translations.add(rus);
                    translations.add(uzKrill);
                    translations.add(uzLot);
                    translations.add(eng);

                    if (translations.size() < 4) {
                        errors.add("Same translation: " + fullMatch);
                        totalErrorCount++;
                    }
                }
            }
        }

        if (totalErrorCount > 0) {
            StringBuilder sb = new StringBuilder();

            // --- Error formatting logic ---

            // 1. To output only 10 file names
            Set<String> fileNames = fileErrors.keySet();
            int displayLimit = 10;
            int filesProcessed = 0;

            for (String fileName : fileNames) {
                if (filesProcessed >= displayLimit) {
                    break;
                }

                int count = fileErrors.get(fileName).size();
                // Format: File: index.jsp (3 errors)
                sb.append("File: ")
                        .append(fileName)
                        .append(" (")
                        .append(count).append(" error")
                        .append(count > 1 ? "s)" : ")")
                        .append("\n");

                filesProcessed++;
            }

            // 2. For files with more than 10...add
            if (fileNames.size() > displayLimit) {
                sb.append("... and there are errors in ")
                        .append(fileNames.size() - displayLimit)
                        .append(" more files.")
                        .append("\n");
            }

            sb.append("\nPlease check the files above..\n");
            sb.append("\nWill you commit anyway?");
            // --- End of error formatting logic ---

            int result = Messages.showDialog(
                    project,
                    sb.toString(),
                    // Total number of errors to the title
                    "SI Translation Validator (total errors: " + totalErrorCount + ")",
                    new String[]{"Commit", "Cancel"},
                    1,
                    Messages.getWarningIcon()
            );

            return result == 0 ? ReturnResult.COMMIT : ReturnResult.CANCEL;
        }

        return ReturnResult.COMMIT;
    }
}