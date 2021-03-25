import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class CustomThreadPool{
    public static void main(String[] args) {
        ThreadPool threadPool = new ThreadPool(1, 1000, TimeUnit.MILLISECONDS,1,
                (queue,task)->{

            //死等
            //queue.put(task);

            //抛出异常
            queue.discard();

            //超时等待
            //queue.offer(task,50,TimeUnit.MILLISECONDS);

            //排队线程太多 放弃了
            //System.out.println("排队线程太多 放弃了... " + task);
                });
        for(int i = 1; i <= 3;i ++){
            int j = i;
            if(j == 4) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("输出：" + j);
                }
            };
            threadPool.execute(runnable);
        }
    }
}


interface RejectPolicy<T>{
    void reject(BlockQueue<T> queue, Runnable task);
}


//线程池实现
class ThreadPool{
    private BlockQueue<Runnable> taskQueue;

    // 线程集合
    private HashSet<Worker> workers = new HashSet<>();

    // 核心线程数
    private int coreSize;

    // 最大线程数
    //private int maxSize;

    // 获取任务时的超时时间
    private long timeout;

    private TimeUnit timeUnit;

    private RejectPolicy<Runnable> rejectPolicy;

    public ThreadPool(int coreSize, long timeout, TimeUnit timeUnit,
                      int queueCapcity, RejectPolicy<Runnable> rejectPolicy) {
        this.coreSize = coreSize;
        //this.maxSize = maxSize;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.taskQueue = new BlockQueue<>(queueCapcity);
        this.rejectPolicy = rejectPolicy;
    }

    public void execute(Runnable task) {
        // 当任务数没有超过 coreSize 时，直接交给 worker 对象执行
        // 如果任务数超过 coreSize 时，加入任务队列暂存
        synchronized (workers) {
            if(workers.size() < coreSize) {
                Worker worker = new Worker(task);
                System.out.println("新增 worker: " + worker + ", " + task);
                workers.add(worker);
                worker.start();
                /*
            } else if(workers.size() < maxSize) {
                // 启动临时线程
                Worker worker = new Worker(task);
                System.out.println("新增 worker启动： " + worker + "," + task);
                workers.add(worker);
                worker.start();

                 */
            } else {
                // 1) 死等
                // 2) 带超时等待
                // 3) 让调用者放弃任务执行
                // 4) 让调用者抛出异常
                // 5) 让调用者自己执行任务
                //taskQueue.tryPut(rejectPolicy, task);
                //taskQueue.put(task);
                taskQueue.tryPut(rejectPolicy, task);
            }
        }
    }
    class Worker extends Thread{
        private Runnable task;
        //public BlockQueue<Runnable> taskQueue;
        public Worker(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            // 执行任务
            // 1) 当 task 不为空，执行任务
            // 2) 当 task 执行完毕，再接着从任务队列获取任务并执行

            //带超时情况
            while(task != null || (task = taskQueue.poll(timeout, timeUnit)) != null){
            //死等情况
            //while(task != null || (task = taskQueue.take()) != null){
                try{
                    System.out.println(task + " 正在执行...");
                    task.run();
                    /*
                    if(workers.size() > coreSize && taskQueue.size() == 0){
                        synchronized (taskQueue){
                            System.out.println("暂时关闭："+"worker: " + this +"已被移除");
                            workers.remove(this);
                            return;
                        }
                   }
                     */
                } catch (Exception e){
                    e.printStackTrace();
                } finally {
                    task = null;
                }
            }

            synchronized (workers) {
                System.out.println("worker: " + this +"已被移除");
                workers.remove(this);
            }
        }
    }
}


//阻塞队列
class BlockQueue<T> {
    // 1. 任务队列
    private Deque<T> queue = new ArrayDeque<>();

    // 2. 锁
    private ReentrantLock lock = new ReentrantLock();

    // 3. 生产者条件变量 队列满需要等待
    private Condition fullWaitSet = lock.newCondition();

    // 4. 消费者条件变量 队列空需要等待
    private Condition emptyWaitSet = lock.newCondition();

    // 5. 阻塞队列容量上限
    private int capcity;

    public BlockQueue(int capcity) {
        this.capcity = capcity;
    }

    //阻塞获取
    public T take(){
        lock.lock();
        try{
            //队列为空需要等待
            while(queue.isEmpty()){
                try {
                    System.out.println("暂时没有任务需要执行...");
                    emptyWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //队列不为空则返回队头元素
            T t = queue.removeFirst();
            //唤醒生产者
            fullWaitSet.signal();
            return t;
        } finally {
            lock.unlock();
        }
    }

    //带超时的阻塞获取
    public T poll(long timeout, TimeUnit unit) {
        lock.lock();
        try {
            // 将 timeout 统一转换为 纳秒
            long nanos = unit.toNanos(timeout);
            while (queue.isEmpty()) {
                try {
                    // 返回值的是剩余时间
                    if (nanos <= 0) {
                        //等待超时
                        System.out.println("等待超时 阻塞队列停止添加任务...");
                        return null;
                    }
                    nanos = emptyWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            T t = queue.removeFirst();
            fullWaitSet.signal();
            return t;
        } finally {
            lock.unlock();
        }
    }

    //阻塞添加
    public void put(T task){
        lock.lock();
        try {
            while (queue.size() == capcity) {
                try {
                    System.out.println("队列已满，" + task + "需等待...");
                    fullWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("成功加入任务队列 " + task);
            queue.addLast(task);
            //唤醒消费者
            emptyWaitSet.signal();
        } finally {
            lock.unlock();
        }
    }

    //带超时时间阻塞添加
    public boolean offer(T task, long timeout, TimeUnit timeUnit){
        lock.lock();
        try {
            long nanos = timeUnit.toNanos(timeout);
            while (queue.size() == capcity) {
                try {
                    System.out.println("队列已满，" + task + "需等待...");
                    if(nanos <= 0){
                        //等待超时 执行失败
                        return false;
                    }
                    nanos = fullWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("成功加入任务队列 " + task);
            queue.addLast(task);
            //唤醒消费者
            emptyWaitSet.signal();
            //添加成功
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void tryPut(RejectPolicy<T> rejectPolicy, T task){
        lock.lock();
        try{
            //判断队列是否满
            if(queue.size() == capcity){
                rejectPolicy.reject(this, (Runnable)task);
            } else{
                System.out.println("成功加入任务队列 " + task);
                queue.addLast(task);
                //唤醒消费者
                emptyWaitSet.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    //获取队列大小
    public int size(){
        lock.lock();
        try{
            return queue.size();
        } finally{
            lock.unlock();
        }
    }
    public void discard() throws DiscardException{
        throw new DiscardException("超时等待");
    }
}
