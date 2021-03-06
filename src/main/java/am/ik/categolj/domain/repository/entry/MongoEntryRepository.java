package am.ik.categolj.domain.repository.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;

import net.reduls.igo.Tagger;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import am.ik.categolj.domain.common.consts.Const;
import am.ik.categolj.domain.common.consts.LogId;
import am.ik.categolj.domain.common.exception.NoSuchEntryException;
import am.ik.categolj.domain.common.util.CommonUtils;
import am.ik.categolj.domain.common.util.TaggerUtils;
import am.ik.categolj.domain.model.Category;
import am.ik.categolj.domain.model.Entry;
import am.ik.categolj.domain.repository.sequencer.MongoSequencer;
import am.ik.yalf.logger.Logger;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Key;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateResults;
import com.mongodb.BasicDBObject;
import com.mongodb.WriteResult;

@Repository
public class MongoEntryRepository implements EntryRepository {
    private static final Logger LOGGER = Logger.getLogger(MongoEntryRepository.class);

    private static final String GET_ENTRY_ORDER = "-updated-at";

    private static final String DISTINCT_CATEGORY = "distinct-category";

    static final String[] RETRIEVE_FIELDS = { "category-index",
            DISTINCT_CATEGORY, "keywords" };

    private static final String DISTINCT_DELIM = "|";

    @Inject
    private Datastore ds;

    @Inject
    private Tagger tagger;

    @Inject
    protected MongoSequencer mongoSequencer;

    public Tagger getTagger() {
        return tagger;
    }

    public void setTagger(Tagger tagger) {
        this.tagger = tagger;
    }

    @Override
    public Entry getEntryById(Long id) throws NoSuchEntryException {
        Entry entry = ds.find(Entry.class, "id", id)
                .retrievedFields(false, RETRIEVE_FIELDS).get();
        LOGGER.debug(LogId.DCTGL009, entry);
        if (entry == null) {
            throw new NoSuchEntryException(id);
        }
        return entry;
    }

    @Override
    public List<Entry> getEntriesByPage(int page, int count) {
        int offset = CommonUtils.calcOffset(page, count);
        List<Entry> entries = ds.find(Entry.class).order(GET_ENTRY_ORDER)
                .retrievedFields(false, RETRIEVE_FIELDS).offset(offset)
                .limit(count).asList();
        return entries;
    }

    @Override
    public List<Entry> getEntriesForGrid(int page, int rows, String sidx,
            String sord) {
        String order = sidx;
        if ("desc".equalsIgnoreCase(sord)) {
            order = "-" + order;
        }
        int offset = CommonUtils.calcOffset(page, rows);
        LOGGER.debug(false, "[DCTGLX20] offset={0}, limit={1}, order={2}",
                offset, rows, order);
        List<Entry> entries = ds
                .find(Entry.class)
                .order(order)
                .retrievedFields(true, "id", "title", "category", "created-at",
                        "updated-at").offset(offset).limit(rows).asList();
        return entries;
    }

    @Override
    public List<String> getAllCategoryPath(String term) {
        LOGGER.debug(false, "[DCTGLX08] autocomplete from term={0}", term);
        List<String> path = new ArrayList<String>();
        if (term != null) {
            Pattern p = Pattern.compile(Pattern.quote(term.replace(
                    Const.CATEGORY_DELIM, DISTINCT_DELIM)),
                    Pattern.CASE_INSENSITIVE);
            @SuppressWarnings("unchecked")
            List<String> categories = ds.getCollection(Entry.class).distinct(
                    DISTINCT_CATEGORY, new BasicDBObject(DISTINCT_CATEGORY, p));
            for (String c : categories) {
                path.add(c.replace(DISTINCT_DELIM, Const.CATEGORY_DELIM));
            }
        }
        return path;
    }

    @Override
    public List<Entry> getEntriesOnlyIdTitle(int count) {
        List<Entry> entries = ds.find(Entry.class).order(GET_ENTRY_ORDER)
                .retrievedFields(true, "id", "title").limit(count).asList();
        return entries;
    }

    @Override
    public int getTotalEntryCount() {
        return (int) ds.getCount(Entry.class);
    }

    protected Query<Entry> getCategorizedQuery(List<Category> category) {

        List<String> cl = new ArrayList<String>();
        for (Category c : category) {
            cl.add(c.getName() + DISTINCT_DELIM + c.getIndex());
        }
        Query<Entry> q = ds.createQuery(Entry.class)
                .filter("category-index all", cl)
                .retrievedFields(false, RETRIEVE_FIELDS);
        LOGGER.debug(LogId.DCTGL010, q);
        return q;
    }

    @Override
    public List<Entry> getCategorizedEntriesByPage(List<Category> category,
            int page, int count) {
        int offset = CommonUtils.calcOffset(page, count);
        List<Entry> entries = getCategorizedQuery(category)
                .order(GET_ENTRY_ORDER).offset(offset).limit(count).asList();
        LOGGER.debug(LogId.DCTGL009, entries);
        return entries;
    }

    @Override
    public int getCategorizeEntryCount(List<Category> category) {
        return (int) getCategorizedQuery(category).countAll();
    }

    public Query<Entry> getKeywordSearchedQuery(Set<String> keywords) {
        Query<Entry> q = ds.createQuery(Entry.class)
                .filter("keywords all", keywords).order(GET_ENTRY_ORDER)
                .retrievedFields(false, RETRIEVE_FIELDS);
        LOGGER.debug(LogId.DCTGL010, q);
        return q;
    }

    public List<Entry> getKeywordSearchedEntriesByPage(Set<String> keywords,
            int page, int count) {
        LOGGER.debug(LogId.DCTGL014, keywords);
        int offset = CommonUtils.calcOffset(page, count);
        List<Entry> entries = getKeywordSearchedQuery(keywords).offset(offset)
                .limit(count).asList();
        LOGGER.debug(LogId.DCTGL009, entries);
        return entries;
    }

    public int getKeywordSearchedEntryCount(Set<String> keywords) {
        Query<Entry> q = getKeywordSearchedQuery(keywords);
        return (int) q.countAll();
    }

    protected Long incrementAndGet() {
        return mongoSequencer.incrementAndGet("entry");
    }

    protected void prepareEntry(Entry entry) {
        List<String> categoriesPath = entry.getCategory();
        List<String> indexes = new ArrayList<String>();
        for (int i = 0; i < categoriesPath.size(); i++) {
            String c = categoriesPath.get(i);
            indexes.add(c + DISTINCT_DELIM + (i + 1));
        }
        entry.setCategoryIndex(indexes);
        entry.setDistinctCategory(StringUtils.collectionToDelimitedString(
                categoriesPath, DISTINCT_DELIM));
        Set<String> keywords = TaggerUtils.extractKeywords(tagger,
                entry.getContent() + " " + entry.getTitle());
        LOGGER.debug(false, "[DCTGLXXX] keywords={0}", keywords);
        entry.setKeywords(keywords);
    }

    @Override
    public void insertEntry(Entry entry) {
        entry.setId(incrementAndGet());
        prepareEntry(entry);
        LOGGER.info(LogId.ICTGL002, entry);
        Key<Entry> key = ds.save(entry);
        LOGGER.info(LogId.ICTGL001, key);
    }

    @Override
    public void updateEntry(Entry entry) {
        prepareEntry(entry);
        LOGGER.info(LogId.ICTGL003, entry);
        Query<Entry> q = ds.find(Entry.class).filter("id", entry.getId());
        UpdateResults<Entry> result = ds.updateFirst(q, entry, false);
        LOGGER.info(LogId.ICTGL001, result);
    }

    @Override
    public void deleteEntry(Entry entry) {
        LOGGER.info(LogId.ICTGL004, entry);
        Query<Entry> q = ds.find(Entry.class).filter("id", entry.getId());
        WriteResult result = ds.delete(q);
        LOGGER.info(LogId.ICTGL001, result);
    }

    public Datastore getDs() {
        return ds;
    }

    public void setDs(Datastore ds) {
        this.ds = ds;
    }
}
