package in.aitechapp.teluguoffline;
import static org.junit.Assert.*;
import android.content.Context;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
@org.robolectric.annotation.Config(sdk = 34)
@RunWith(RobolectricTestRunner.class)
public class RenderQueueTest {
  @Test public void redeliveryKeepsIndependentSubmissionsInOrder() {
    var prefs = RuntimeEnvironment.getApplication().getSharedPreferences("queue-test", Context.MODE_PRIVATE);
    var q = new RenderQueue(prefs);
    String a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString(), c=UUID.randomUUID().toString();
    q.add(a,"Srinivas"); q.add(b,"Karthik"); q.add(c,"Srinivas");
    q=new RenderQueue(prefs);
    q.add(b,"Karthik");
    assertEquals("Rendering: Srinivas · Queued: Karthik · Queued: Srinivas",q.summary());
    assertEquals(a,q.first()); q.finish(a);
    assertEquals(b,new RenderQueue(prefs).first()); q.finish(b); q.finish(c);
    assertNull(new RenderQueue(prefs).first());
  }
}
