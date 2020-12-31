/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.execution;

import com.google.common.util.concurrent.ListenableFuture;
import io.trino.Session;
import io.trino.metadata.Metadata;
import io.trino.security.AccessControl;
import io.trino.spi.PrestoException;
import io.trino.spi.connector.CatalogSchemaName;
import io.trino.sql.tree.Expression;
import io.trino.sql.tree.Use;
import io.trino.transaction.TransactionManager;

import java.util.List;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static io.trino.spi.StandardErrorCode.MISSING_CATALOG_NAME;
import static io.trino.spi.StandardErrorCode.NOT_FOUND;
import static io.trino.sql.analyzer.SemanticExceptions.semanticException;
import static java.util.Locale.ENGLISH;

public class UseTask
        implements DataDefinitionTask<Use>
{
    @Override
    public String getName()
    {
        return "USE";
    }

    @Override
    public ListenableFuture<?> execute(Use statement, TransactionManager transactionManager, Metadata metadata, AccessControl accessControl, QueryStateMachine stateMachine, List<Expression> parameters)
    {
        Session session = stateMachine.getSession();

        String catalog = statement.getCatalog()
                .map(identifier -> identifier.getValue().toLowerCase(ENGLISH))
                .orElseGet(() -> session.getCatalog().orElseThrow(() ->
                        semanticException(MISSING_CATALOG_NAME, statement, "Catalog must be specified when session catalog is not set")));

        if (metadata.getCatalogHandle(session, catalog).isEmpty()) {
            throw new PrestoException(NOT_FOUND, "Catalog does not exist: " + catalog);
        }

        String schema = statement.getSchema().getValue().toLowerCase(ENGLISH);

        CatalogSchemaName name = new CatalogSchemaName(catalog, schema);
        if (!metadata.schemaExists(session, name)) {
            throw new PrestoException(NOT_FOUND, "Schema does not exist: " + name);
        }

        if (statement.getCatalog().isPresent()) {
            stateMachine.setSetCatalog(catalog);
        }
        stateMachine.setSetSchema(schema);

        return immediateFuture(null);
    }
}