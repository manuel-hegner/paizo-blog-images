package paizo.crawler;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MyPool {
	
	private final String name;
	private final ExecutorService pool = Executors.newWorkStealingPool();
	private final AtomicInteger submitted = new AtomicInteger();
	private final AtomicInteger done = new AtomicInteger();

	public <T> Future<T> submit(Callable<T> job) {
		submitted.incrementAndGet();
		return pool.submit(new Callable<T>() {
			@Override
			public T call() throws Exception {
				try {
					return job.call();
				} finally {
					done.incrementAndGet();
				}
			}
		});
	}

	public void shutdown() throws InterruptedException {
		pool.shutdown();
		System.out.println(name+": submitted "+submitted.get()+" taks");
		while(!pool.isTerminated()) {
			Thread.sleep(1000);
			System.out.println(name+": \tdone "+done.get()+"/"+submitted.get());
		}
	}
	
}
