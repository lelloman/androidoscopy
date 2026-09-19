//! One stdio MCP server per selected app instance; tool definitions come from it.
use crate::control;
use reqwest::Method;
use rmcp::{model::*, service::RequestContext, ErrorData, RoleServer, ServerHandler, ServiceExt};
use serde_json::{json, Value};

#[derive(Clone)]
struct Bridge {
    device: String,
}
impl Bridge {
    async fn snapshot(&self) -> anyhow::Result<Value> {
        control::request(Method::GET, &format!("session/{}", self.device), None).await
    }
}
impl ServerHandler for Bridge {
    fn get_info(&self) -> ServerInfo {
        ServerInfo {
            capabilities: ServerCapabilities::builder().enable_tools().enable_tool_list_changed().build(),
            instructions: Some("Tools are defined and executed by the selected Android app. Start and authorize its diagnostic session on the device before calling tools.".into()),
            ..Default::default()
        }
    }
    async fn list_tools(
        &self,
        _: Option<PaginatedRequestParam>,
        _: RequestContext<RoleServer>,
    ) -> Result<ListToolsResult, ErrorData> {
        let state = self
            .snapshot()
            .await
            .map_err(|e| ErrorData::internal_error(e.to_string(), None))?;
        let tools = serde_json::from_value(state["tools"].clone()).map_err(|e| {
            ErrorData::internal_error(format!("Invalid app tool manifest: {e}"), None)
        })?;
        Ok(ListToolsResult {
            tools,
            ..Default::default()
        })
    }
    async fn call_tool(
        &self,
        request: CallToolRequestParam,
        context: RequestContext<RoleServer>,
    ) -> Result<CallToolResult, ErrorData> {
        let id = uuid::Uuid::new_v4().to_string();
        let body = json!({"device":self.device,"id":id,"name":request.name,"arguments":request.arguments.unwrap_or_default()});
        let result = tokio::select! {
            _ = context.ct.cancelled() => {
                let _ = control::request(Method::POST,"cancel",Some(json!({"device":self.device,"id":id}))).await;
                return Ok(CallToolResult::error(vec![Content::text("CANCELLED")]));
            }
            result = control::request(Method::POST,"call",Some(body)) => result,
        };
        match result {
            Ok(result) => serde_json::from_value(result)
                .map_err(|e| ErrorData::internal_error(e.to_string(), None)),
            Err(error) => Ok(CallToolResult::error(vec![Content::text(
                error.to_string(),
            )])),
        }
    }
}
pub async fn run(device: String) -> anyhow::Result<()> {
    anyhow::ensure!(
        device
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-'),
        "invalid device ID"
    );
    let bridge = Bridge { device };
    let service = bridge.clone().serve(rmcp::transport::stdio()).await?;
    let peer = service.peer().clone();
    let notifier = tokio::spawn(async move {
        let mut previous = Value::Null;
        loop {
            let tools = bridge
                .snapshot()
                .await
                .map(|s| s["tools"].clone())
                .unwrap_or(json!([]));
            if tools != previous {
                previous = tools;
                if peer.notify_tool_list_changed().await.is_err() {
                    break;
                }
            }
            tokio::time::sleep(std::time::Duration::from_secs(1)).await;
        }
    });
    let result = service.waiting().await;
    notifier.abort();
    result?;
    Ok(())
}
