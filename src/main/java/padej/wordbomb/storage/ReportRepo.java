package padej.wordbomb.storage;

/** Жалобы на слова словаря ({@code /wb report}): таблица {@code reports}, бывший {@code reports.txt}. */
public final class ReportRepo extends VoteRepo {

    public ReportRepo(Database db) {
        super(db, "reports");
    }
}
