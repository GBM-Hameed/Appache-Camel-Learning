package com.learncamel.routes;

import com.learncamel.alert.MailProcessor;
import com.learncamel.domain.Item;
import com.learncamel.exception.DataException;
import com.learncamel.processor.BuildSQLProcessor;
import com.learncamel.processor.ValidateDataProcessor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.gson.GsonDataFormat;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Created by z001qgd on 1/3/18.
 */
@Component
public class ActiveMQRoute extends RouteBuilder{

    @Autowired
    Environment environment;

    @Qualifier("dataSource")
    @Autowired
    DataSource dataSource;

    @Autowired
    MailProcessor mailProcessor;

    @Autowired
    ValidateDataProcessor validateProcessor;

    @Autowired
    BuildSQLProcessor sqlProcessor;



    @Override
    public void configure() throws Exception {

        Predicate isNotMock = header("env").isNotEqualTo("mock");

        GsonDataFormat itemFormat = new GsonDataFormat(Item.class);

        onException(PSQLException.class).log(LoggingLevel.ERROR,"PSQLException in the route ${body}")
                .maximumRedeliveries(3).redeliveryDelay(3000).backOffMultiplier(2).retryAttemptedLogLevel(LoggingLevel.ERROR);

        onException(DataException.class,RuntimeException.class).log(LoggingLevel.ERROR, "DataException in the route ${body}")
                .choice()
                .when(isNotMock)
                .process(mailProcessor)
                .end()
                .log("Body in Exception Block is ${body}")
                .setBody(constant(body()))
                .to("{{errorRoute}}");



        from("{{fromRoute}}")
                .log("Read Message from ActiveMQ ${body}")
                .unmarshal(itemFormat)
                .log("UnMarshaled Message is ${body}")
                .process(validateProcessor)
                .process(sqlProcessor)
                .to("{{toRoute}}")
                .to("{{selectNode}}")
                .log("Result from the db table is ${body}");;

        }
}
