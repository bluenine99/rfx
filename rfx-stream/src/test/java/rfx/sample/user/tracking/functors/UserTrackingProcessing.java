package rfx.sample.user.tracking.functors;

import java.util.Date;

import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.ShardedJedisPool;
import redis.clients.jedis.exceptions.JedisException;
import rfx.core.configs.RedisConfigs;
import rfx.core.nosql.jedis.RedisCommand;
import rfx.core.stream.functor.StreamProcessor;
import rfx.core.stream.message.Tuple;
import rfx.core.stream.model.DataFlowInfo;
import rfx.core.stream.topology.BaseTopology;
import rfx.core.util.DateTimeUtil;
import rfx.core.util.LogUtil;
import rfx.core.util.StringUtil;
import rfx.core.util.useragent.Parser;

public class UserTrackingProcessing extends StreamProcessor  {
	
	static Parser uaParser = Parser.load();
	static ShardedJedisPool jedisPool = RedisConfigs.load().get("realtimeDataStats").getShardedJedisPool();
	
	protected UserTrackingProcessing(DataFlowInfo dataFlowInfo, BaseTopology topology) {
		super(dataFlowInfo, topology);
	}
	
	@Override
	public void onReceive(Tuple inputTuple) throws Exception {		
		try {
			String partitionId = inputTuple.getStringByField("partitionId");
			this.counter(StringUtil.toString(this.getMetricKey(), partitionId)).incrementAndGet();
						
			final long loggedTime= inputTuple.getLongByField("loggedtime");
			final String uuid    	= inputTuple.getStringByField("uuid");
			final String event    = inputTuple.getStringByField("event");
			final String url   	= inputTuple.getStringByField("url");			
			
			System.out.println(loggedTime + " " + uuid + " " + event + " " + url);
			(new RedisCommand<Long>(jedisPool) {
	            @Override
	            public Long build() throws JedisException {
	            	Pipeline p = jedis.pipelined();
	            	long delta = 1L;
	            	Date loggedDate = new Date(loggedTime);			            
	            	String minuteStr = DateTimeUtil.formatDateHourMinute(loggedDate);
	            	p.hincrBy(minuteStr,uuid, delta);			            	
	            	p.hincrBy(minuteStr,uuid+"-"+event, delta);		            	
	            	Response<Long> count = p.incrBy(event, delta);			            	
	            	p.sync();		            				            	
	                return count.get();
	            }
		    }).execute();
		
		} catch (IllegalArgumentException e) {
			LogUtil.error(e);
		} catch (Exception e) {
			LogUtil.error(e);
			//log.error(ExceptionUtils.getStackTrace(e));
		} finally {
			inputTuple.clear();
		}		
		this.doPostProcessing();
	}

}
