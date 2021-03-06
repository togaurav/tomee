/**
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
package org.apache.tomee.catalina.cdi;

import org.apache.openejb.server.httpd.EndWebBeansListener;
import org.apache.tomee.catalina.TomEERuntimeException;
import org.apache.webbeans.context.creational.BeanInstanceBag;
import org.apache.webbeans.intercept.NormalScopedBeanInterceptorHandler;

import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class SessionNormalScopeBeanHandler extends NormalScopedBeanInterceptorHandler {
    private static final Field BAG_INSTANCE;
    static {
        try {
            BAG_INSTANCE = BeanInstanceBag.class.getDeclaredField("beanInstance");
            BAG_INSTANCE.setAccessible(true);
        } catch (final NoSuchFieldException e) {
            throw new TomEERuntimeException(e);
        }
    }

    private final ThreadLocal<Map<Bean<?>, UpdateInfo>> objects = new ThreadLocal<Map<Bean<?>, UpdateInfo>>() {
        @Override
        protected Map<Bean<?>, UpdateInfo> initialValue() {
            EndWebBeansListener.pushRequestReleasable(new Runnable() { // update in batch
                @Override
                public void run() {
                    final Map<Bean<?>, UpdateInfo> values = objects.get();
                    for (final UpdateInfo o : values.values()) {
                        o.updateBean();
                    }
                    values.clear();
                    objects.remove();
                }
            });
            return new HashMap<Bean<?>, UpdateInfo>();
        }
    };

    public SessionNormalScopeBeanHandler(final BeanManager beanManager, final Bean<?> bean) {
        super(beanManager, bean);
    }

    @Override
    public Object get() {
        final Object webbeansInstance = getContextualInstance();
        final Map<Bean<?>, UpdateInfo> beanUpdateInfoMap = objects.get();

        UpdateInfo info = beanUpdateInfoMap.get(this);
        if (info == null) {
            info = new UpdateInfo(bean, getBeanManager(), webbeansInstance);
            beanUpdateInfoMap.put(bean, info);
        }

        return webbeansInstance;
    }

    protected static class UpdateInfo {
        private Bean<?> bean;
        private BeanManager bm;
        private Object value;

        protected UpdateInfo(final Bean<?> bean, final BeanManager bm, final Object value) {
            this.bean = bean;
            this.bm = bm;
            this.value = value;
        }

        protected void updateBean() {
            final HttpSession session = session();
            if (session == null) {
                return;
            }

            // go through all listeners to be able to be replicated or do any processing which can be done
            final String key = SessionContextBackedByHttpSession.key(bean);
            final BeanInstanceBag<Object> bag = BeanInstanceBag.class.cast(session.getAttribute(key));
            if (bag != null) {
                try {
                    BAG_INSTANCE.set(bag, value);
                } catch (final IllegalAccessException e) {
                    throw new TomEERuntimeException(e);
                }
                session.setAttribute(key, bag);
            }
        }

        private HttpSession session() {
            final Context context = bm.getContext(SessionScoped.class);
            if (!SessionContextBackedByHttpSession.class.isInstance(context)) {
                return null;
            }
            return SessionContextBackedByHttpSession.class.cast(context).getSession();
        }
    }
}
