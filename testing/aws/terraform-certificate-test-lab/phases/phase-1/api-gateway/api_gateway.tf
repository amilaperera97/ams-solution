locals {
  create                   = var.enable_api_gateway
  create_acm_issued_domain = local.create && var.acm_issued_certificate_arn != null && var.acm_issued_domain_name != null
}

# ---------------------------------------------------------------------------
# Minimal REST API: root resource, GET method, MOCK integration.
# ---------------------------------------------------------------------------
resource "aws_api_gateway_rest_api" "this" {
  count = local.create ? 1 : 0

  name        = "${var.name_prefix}-api"
  description = "Certificate test lab minimal API - exists only to host custom domain names"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-api" })
}

resource "aws_api_gateway_method" "root_get" {
  count = local.create ? 1 : 0

  rest_api_id   = aws_api_gateway_rest_api.this[0].id
  resource_id   = aws_api_gateway_rest_api.this[0].root_resource_id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "root_get" {
  count = local.create ? 1 : 0

  rest_api_id = aws_api_gateway_rest_api.this[0].id
  resource_id = aws_api_gateway_rest_api.this[0].root_resource_id
  http_method = aws_api_gateway_method.root_get[0].http_method
  type        = "MOCK"

  request_templates = {
    "application/json" = jsonencode({ statusCode = 200 })
  }
}

resource "aws_api_gateway_method_response" "root_get" {
  count = local.create ? 1 : 0

  rest_api_id = aws_api_gateway_rest_api.this[0].id
  resource_id = aws_api_gateway_rest_api.this[0].root_resource_id
  http_method = aws_api_gateway_method.root_get[0].http_method
  status_code = "200"
}

resource "aws_api_gateway_integration_response" "root_get" {
  count = local.create ? 1 : 0

  rest_api_id = aws_api_gateway_rest_api.this[0].id
  resource_id = aws_api_gateway_rest_api.this[0].root_resource_id
  http_method = aws_api_gateway_method.root_get[0].http_method
  status_code = aws_api_gateway_method_response.root_get[0].status_code

  response_templates = {
    "application/json" = jsonencode({ message = "certificate-test-lab" })
  }

  depends_on = [aws_api_gateway_integration.root_get]
}

resource "aws_api_gateway_deployment" "this" {
  count = local.create ? 1 : 0

  rest_api_id = aws_api_gateway_rest_api.this[0].id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_method.root_get[0].id,
      aws_api_gateway_integration.root_get[0].id,
      aws_api_gateway_integration_response.root_get[0].id,
    ]))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "this" {
  count = local.create ? 1 : 0

  rest_api_id   = aws_api_gateway_rest_api.this[0].id
  deployment_id = aws_api_gateway_deployment.this[0].id
  stage_name    = var.stage_name

  tags = merge(var.tags, { Name = "${var.name_prefix}-api-${var.stage_name}" })
}

# ---------------------------------------------------------------------------
# Custom domain backed by the ACM IMPORT - always created.
# SCENARIO phase1-cert-019
# ---------------------------------------------------------------------------
resource "aws_api_gateway_domain_name" "imported" {
  count = local.create ? 1 : 0

  domain_name              = var.imported_domain_name
  regional_certificate_arn = var.imported_certificate_arn
  security_policy          = var.security_policy

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = merge(var.tags, lookup(var.certificate_tags, var.imported_certificate_id, {}), {
    Name = var.imported_domain_name
  })
}

resource "aws_api_gateway_base_path_mapping" "imported" {
  count = local.create ? 1 : 0

  api_id      = aws_api_gateway_rest_api.this[0].id
  stage_name  = aws_api_gateway_stage.this[0].stage_name
  domain_name = aws_api_gateway_domain_name.imported[0].domain_name
}

# ---------------------------------------------------------------------------
# Custom domain backed by the ACM-ISSUED certificate - only when that
# certificate is actually ISSUED and therefore attachable.
# SCENARIO phase1-cert-004
# ---------------------------------------------------------------------------
resource "aws_api_gateway_domain_name" "acm_issued" {
  count = local.create_acm_issued_domain ? 1 : 0

  domain_name              = var.acm_issued_domain_name
  regional_certificate_arn = var.acm_issued_certificate_arn
  security_policy          = var.security_policy

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = merge(var.tags, lookup(var.certificate_tags, var.acm_issued_certificate_id, {}), {
    Name = var.acm_issued_domain_name
  })
}

resource "aws_api_gateway_base_path_mapping" "acm_issued" {
  count = local.create_acm_issued_domain ? 1 : 0

  api_id      = aws_api_gateway_rest_api.this[0].id
  stage_name  = aws_api_gateway_stage.this[0].stage_name
  domain_name = aws_api_gateway_domain_name.acm_issued[0].domain_name
}

# ---------------------------------------------------------------------------
# Custom domain relationship table. One list literal with an "exists" flag
# rather than concat()-ing conditional lists, for the same type-unification
# reason documented in load-balancers/locals.tf.
# ---------------------------------------------------------------------------
locals {
  custom_domain_candidates = [
    {
      exists                   = local.create
      certificate_id           = var.imported_certificate_id
      domain_name              = one(aws_api_gateway_domain_name.imported[*].domain_name)
      regional_certificate_arn = one(aws_api_gateway_domain_name.imported[*].regional_certificate_arn)
      regional_domain_name     = one(aws_api_gateway_domain_name.imported[*].regional_domain_name)
      endpoint_type            = "REGIONAL"
      security_policy          = one(aws_api_gateway_domain_name.imported[*].security_policy)
      base_path_mapped_stage   = one(aws_api_gateway_base_path_mapping.imported[*].stage_name)
    },
    {
      exists                   = local.create_acm_issued_domain
      certificate_id           = var.acm_issued_certificate_id
      domain_name              = one(aws_api_gateway_domain_name.acm_issued[*].domain_name)
      regional_certificate_arn = one(aws_api_gateway_domain_name.acm_issued[*].regional_certificate_arn)
      regional_domain_name     = one(aws_api_gateway_domain_name.acm_issued[*].regional_domain_name)
      endpoint_type            = "REGIONAL"
      security_policy          = one(aws_api_gateway_domain_name.acm_issued[*].security_policy)
      base_path_mapped_stage   = one(aws_api_gateway_base_path_mapping.acm_issued[*].stage_name)
    },
  ]
}
