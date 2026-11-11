package dev.luisvergara.manabi.admin.security;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
/** Bounded local throttling per peer address and account. No forwarded-header trust or credentials stored. */
@Component
public class LoginThrottle {
 private record Window(long start,int attempts){}
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 private final Clock clock;private final int limit;private final long duration;
 public LoginThrottle(){this(Clock.systemUTC(),10,15*60*1000L);}
 LoginThrottle(Clock clock,int limit,long duration){this.clock=clock;this.limit=limit;this.duration=duration;}
 public boolean allow(String peer,String username){
  long now=clock.millis();
  if(windows.size()>=10000){windows.entrySet().removeIf(e->now-e.getValue().start()>=duration);if(windows.size()>=10000)return false;}
  return hit("peer:"+peer,now)&&hit("account:"+username,now);
 }
 private boolean hit(String key,long now){
  var result=windows.compute(key,(k,v)->v==null||now-v.start()>=duration?new Window(now,1):new Window(v.start(),Math.min(v.attempts()+1,limit+1)));
  return result.attempts()<=limit;
 }
}
