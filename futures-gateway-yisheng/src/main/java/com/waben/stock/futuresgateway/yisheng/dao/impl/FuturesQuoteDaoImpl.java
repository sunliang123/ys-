package com.waben.stock.futuresgateway.yisheng.dao.impl;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.waben.stock.futuresgateway.yisheng.dao.FuturesQuoteDao;
import com.waben.stock.futuresgateway.yisheng.entity.FuturesQuote;

/**
 * 期货合约行情 Dao实现
 * 
 * @author lma
 *
 */
@Repository
public class FuturesQuoteDaoImpl implements FuturesQuoteDao {

	@Autowired
	private MongoTemplate mongoTemplate;

	private static String quoteCollectionNamePrefix = "quote-";

	@Override
	public FuturesQuote createFuturesQuote(FuturesQuote futuresQuote) {
		futuresQuote.setId(new ObjectId().toString());
		mongoTemplate.save(futuresQuote,
				quoteCollectionNamePrefix + futuresQuote.getCommodityNo() + "-" + futuresQuote.getContractNo());
		return futuresQuote;
	}

	@Override
	public void deleteFuturesQuoteById(String commodityNo, String contractNo, String id) {
		Query query = new Query();
		query.addCriteria(Criteria.where("id").is(id));
		mongoTemplate.remove(query, FuturesQuote.class, quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public void deleteFuturesQuoteByDateTimeStampLessThan(String commodityNo, String contractNo, String dateTimeStamp) {
		Query query = new Query();
		query.addCriteria(Criteria.where("dateTimeStamp").lt(dateTimeStamp));
		mongoTemplate.remove(query, FuturesQuote.class, quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public FuturesQuote retrieveFuturesQuoteById(String commodityNo, String contractNo, String id) {
		Query query = new Query();
		query.addCriteria(Criteria.where("id").is(id));
		return mongoTemplate.findOne(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public Page<FuturesQuote> pageFuturesQuote(String commodityNo, String contractNo, int page, int limit) {
		Query query = new Query();
		query.skip(page * limit);
		query.limit(limit);
		List<FuturesQuote> content = mongoTemplate.find(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
		long total = mongoTemplate.count(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
		Page<FuturesQuote> result = new PageImpl<>(content, new PageRequest(page, limit), total);
		return result;
	}

	@Override
	public Page<FuturesQuote> pageFuturesQuoteByDateTimeStampLessThan(String commodityNo, String contractNo, int page,
			int limit, String dateTimeStamp) {
		Query query = new Query();
		query.addCriteria(Criteria.where("dateTimeStamp").lt(dateTimeStamp));
		query.skip(page * limit);
		query.limit(limit);
		query.with(new Sort(new Sort.Order(Direction.ASC, "dateTimeStamp")));
		List<FuturesQuote> content = mongoTemplate.find(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
		long total = mongoTemplate.count(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
		Page<FuturesQuote> result = new PageImpl<>(content, new PageRequest(page, limit), total);
		return result;
	}

	@Override
	public List<FuturesQuote> listFuturesQuote(String commodityNo, String contractNo) {
		Query query = new Query();
		return mongoTemplate.find(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public List<FuturesQuote> retrieveByCommodityNoAndContractNoAndDateTimeStampLike(String commodityNo,
			String contractNo, String dateTimeStamp) {
		Query query = new Query();
		Pattern pattern = Pattern.compile("^" + dateTimeStamp + ".*", Pattern.CASE_INSENSITIVE);
		query.addCriteria(Criteria.where("dateTimeStamp").regex(pattern));
		query.with(
				new Sort(new Sort.Order(Direction.ASC, "dateTimeStamp"), new Sort.Order(Direction.ASC, "quoteIndex")));
		return mongoTemplate.find(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public Long countByTimeGreaterThanEqual(String commodityNo, String contractNo, Date time) {
		Query query = new Query();
		query.addCriteria(Criteria.where("time").gte(time));
		return mongoTemplate.count(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public FuturesQuote retriveNewest(String commodityNo, String contractNo) {
		Query query = new Query();
		query.skip(0);
		query.limit(1);
		query.with(new Sort(new Sort.Order(Direction.DESC, "dateTimeStamp")));
		return mongoTemplate.findOne(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public FuturesQuote miniteFirst(String commodityNo, String contractNo, String dateTimeStamp) {
		Query query = new Query();
		query.skip(0);
		query.limit(1);
		Pattern pattern = Pattern.compile("^" + dateTimeStamp + ".*", Pattern.CASE_INSENSITIVE);
		query.addCriteria(Criteria.where("dateTimeStamp").regex(pattern));
		query.with(
				new Sort(new Sort.Order(Direction.ASC, "dateTimeStamp"), new Sort.Order(Direction.ASC, "quoteIndex")));
		return mongoTemplate.findOne(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public FuturesQuote miniteLast(String commodityNo, String contractNo, String dateTimeStamp) {
		Query query = new Query();
		query.skip(0);
		query.limit(1);
		Pattern pattern = Pattern.compile("^" + dateTimeStamp + ".*", Pattern.CASE_INSENSITIVE);
		query.addCriteria(Criteria.where("dateTimeStamp").regex(pattern));
		query.with(new Sort(new Sort.Order(Direction.DESC, "dateTimeStamp"),
				new Sort.Order(Direction.DESC, "quoteIndex")));
		return mongoTemplate.findOne(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public FuturesQuote minuteMax(String commodityNo, String contractNo, String dateTimeStamp) {
		Query query = new Query();
		query.skip(0);
		query.limit(1);
		Pattern pattern = Pattern.compile("^" + dateTimeStamp + ".*", Pattern.CASE_INSENSITIVE);
		query.addCriteria(Criteria.where("dateTimeStamp").regex(pattern));
		query.with(new Sort(new Sort.Order(Direction.DESC, "lastPrice")));
		return mongoTemplate.findOne(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public FuturesQuote minuteMin(String commodityNo, String contractNo, String dateTimeStamp) {
		Query query = new Query();
		query.skip(0);
		query.limit(1);
		Pattern pattern = Pattern.compile("^" + dateTimeStamp + ".*", Pattern.CASE_INSENSITIVE);
		query.addCriteria(Criteria.where("dateTimeStamp").regex(pattern));
		query.with(new Sort(new Sort.Order(Direction.ASC, "lastPrice")));
		return mongoTemplate.findOne(query, FuturesQuote.class,
				quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}

	@Override
	public void minuteAllQuoteDel(String commodityNo, String contractNo, String dateTimeStamp) {
		Query query = new Query();
		query.skip(0);
		query.limit(1);
		Pattern pattern = Pattern.compile("^" + dateTimeStamp + ".*", Pattern.CASE_INSENSITIVE);
		query.addCriteria(Criteria.where("dateTimeStamp").regex(pattern));
		mongoTemplate.remove(query, FuturesQuote.class, quoteCollectionNamePrefix + commodityNo + "-" + contractNo);
	}
	
}
