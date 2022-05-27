/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.rocketmq.example.batch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.sysflag.MessageSysFlag;
import org.apache.rocketmq.example.NameSrvEnum;

/**
 * 批量发送消息能显著提高传递小消息的性能。
 * 限制是这些批量消息应该有相同的topic，相同的waitStoreMsgOK，而且不能是延时消息。
 * 此外，这一批消息的总大小不应超过4MB。
 */
public class SplitBatchProducer {
    private static final String TOPIC = "BatchTest";

    public static void main(String[] args) throws Exception {

        DefaultMQProducer producer = new DefaultMQProducer("BatchProducerGroupName");
        producer.setNamesrvAddr(NameSrvEnum.DEV_SRV.getAddr());
        producer.setSendMsgTimeout(10000);
        producer.start();

        //large batch
        List<Message> messages = new ArrayList<>(100 * 1000);
        for (int i = 0; i < 100 * 1000; i++) {
            messages.add(new Message(TOPIC, "Tag", "OrderID" + i, ("Hello world " + i).getBytes()));
        }

        //split the large batch into small ones:
        ListSplitter splitter = new ListSplitter(messages);
        while (splitter.hasNext()) {
            List<Message> listItem = splitter.next();
            producer.send(listItem);
        }

        producer.shutdown();
    }
}

 /*class ListSplitter implements Iterator<List<Message>> {
     private int sizeLimit = 1024 * 1024 * 4;
     private final List<Message> messages;
     private int currIndex;

     public ListSplitter(List<Message> messages) {
         this.messages = messages;
     }

     @Override
     public boolean hasNext() {
         return currIndex < messages.size();
     }

     @Override
     public List<Message> next() {
         int nextIndex = currIndex;
         int totalSize = 0;
         for (; nextIndex < messages.size(); nextIndex++) {
             Message message = messages.get(nextIndex);
             int tmpSize = message.getTopic().length() + message.getBody().length;
}            Map<String, String> properties = message.getProperties();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                tmpSize += entry.getKey().length() + entry.getValue().length();
            }
            tmpSize = tmpSize + 20; //for log overhead
            if (tmpSize > sizeLimit) {
                //it is unexpected that single message exceeds the sizeLimit
                //here just let it go, otherwise it will block the splitting process
                if (nextIndex - currIndex == 0) {
                    //if the next sublist has no element, add this one and then break, otherwise just break
                    nextIndex++;
                }
                break;
            }
            if (tmpSize + totalSize > sizeLimit) {
                break;
            } else {
                totalSize += tmpSize;
            }

        }
        List<Message> subList = messages.subList(currIndex, nextIndex);
        currIndex = nextIndex;
        return subList;
    }*/

class ListSplitter implements Iterator<List<Message>> {
    private final int SIZE_LIMIT = 1000 * 1000 * 2;
    private final List<Message> messages;
    private int currIndex;

    public ListSplitter(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public boolean hasNext() {
        return currIndex < messages.size();
    }

    @Override
    public List<Message> next() {
        int startIndex = getStartIndex();
        int nextIndex = startIndex;
        int totalSize = 0;
        for (; nextIndex < messages.size(); nextIndex++) {
            Message message = messages.get(nextIndex);
            int tmpSize = calcMessageSize(message);
            if (tmpSize + totalSize > SIZE_LIMIT) {
                break;
            } else {
                totalSize += tmpSize;
            }
        }
        List<Message> subList = messages.subList(startIndex, nextIndex);
        currIndex = nextIndex;
        return subList;
    }

    private int getStartIndex() {
        Message currMessage = messages.get(currIndex);
        int tmpSize = calcMessageSize(currMessage);
        // 如果单条消息超过阈值，把这条跳过，避免程序报错
        while (tmpSize > SIZE_LIMIT) {
            currIndex += 1;
            Message message = messages.get(currIndex);
            tmpSize = calcMessageSize(message);
        }
        return currIndex;
    }

    private int calcMessageSizeOld(Message message) {
        int tmpSize = message.getTopic().length() + message.getBody().length;
        Map<String, String> properties = message.getProperties();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            tmpSize += entry.getKey().length() + entry.getValue().length();
        }
        tmpSize = tmpSize + 20; // 增加⽇日志的开销20字节
        return tmpSize;
    }

    private int calcMessageSize(Message msg) {
        Map<String, String> properties = msg.getProperties();
        int propLength = 0;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            propLength += entry.getKey().length() + entry.getValue().length();
        }
        return calMsgLength(0, msg.getBody().length, msg.getTopic().length(),propLength);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Not allowed to remove");
    }

    protected static int calMsgLength(int sysFlag, int bodyLength, int topicLength, int propertiesLength) {
        int bornhostLength = (sysFlag & MessageSysFlag.BORNHOST_V6_FLAG) == 0 ? 8 : 20;
        int storehostAddressLength = (sysFlag & MessageSysFlag.STOREHOSTADDRESS_V6_FLAG) == 0 ? 8 : 20;
        final int msgLen = 4 //TOTALSIZE
                + 4 //MAGICCODE
                + 4 //BODYCRC
                + 4 //QUEUEID
                + 4 //FLAG
                + 8 //QUEUEOFFSET
                + 8 //PHYSICALOFFSET
                + 4 //SYSFLAG
                + 8 //BORNTIMESTAMP
                + bornhostLength //BORNHOST
                + 8 //STORETIMESTAMP
                + storehostAddressLength //STOREHOSTADDRESS
                + 4 //RECONSUMETIMES
                + 8 //Prepared Transaction Offset
                + 4 + (bodyLength > 0 ? bodyLength : 0) //BODY
                + 1 + topicLength //TOPIC
                + 2 + (propertiesLength > 0 ? propertiesLength : 0) //propertiesLength
                + 0;
        return msgLen;
    }
}