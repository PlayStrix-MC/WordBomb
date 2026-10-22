package padej.wordbomb.storage;

/** Предложения новых слов ({@code /wb offer}): таблица {@code suggestions}, бывший {@code offers.txt}. */
public final class SuggestionRepo extends VoteRepo {

    public SuggestionRepo(Database db) {
        super(db, "suggestions");
    }
}
